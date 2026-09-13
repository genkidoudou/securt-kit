# Monitor 刷数作业异步化设计

> 状态：已确认（待实现）  
> 版本：v1.0  
> 日期：2026-09-10  
> 目的：让约 1 万行量级的表刷数（encrypt / decrypt / sign）可异步执行、可看进度、可暂停/继续/取消；作业状态支持内存或目标库双模式。

---

## 1. 背景与问题

### 1.1 现状

当前 Monitor 刷数（`MonitorOpsFacade.batchPreview` / `batchApply` + `BatchJobStore`）模型为：

1. Preview：`SELECT … LIMIT 501`，将**全部匹配行**的 before/after 载入 JVM  
2. 内存作业 TTL（默认 10 分钟），`MAX_ROWS = 500`  
3. Apply：同一次 HTTP 请求内按主键逐行 `UPDATE`（`SECURT_SKIP`）

### 1.2 大表下为何不可行

| 瓶颈 | 影响 |
|------|------|
| 硬顶 500 | 1 万行直接被拒 |
| 全量 before/after 进内存 | 内存与序列化膨胀；浏览器也难承载 |
| 同步 HTTP apply | 易超时；无进度 |
| 无游标/检查点 | 中断后无法续跑 |
| 无暂停/取消 | 误跑只能杀进程 |
| 多表勾选 UX | 前端易只保留最后一个 `jobId` |

结论：**现有方式仅适合小表运维预览；对 ≥1 万行不可行。**

### 1.3 目标（本阶段）

1. 约 **1 万行**（软/硬顶默认 **2 万**，可配置）可跑通 encrypt/decrypt/sign  
2. **样本预览** + **异步执行** + UI **轮询进度**  
3. 控制：**暂停 / 继续 / 取消**  
4. 单行失败：记录后继续；终态可为 **PARTIAL**  
5. 作业存储：**memory（默认）** 或 **database（目标库系统表，须落地）**  
6. 业务行**永不**整表缓存进作业快照  

### 1.4 非目标（本阶段）

- 百万级吞吐优化与多 Monitor 实例抢占调度  
- 多表合并为一个作业  
- 导出变更 SQL / 审计日志完整产品化  
- 替换或重做数据初始化（data-init）整页  

---

## 2. 决策摘要

| 议题 | 决策 |
|------|------|
| 水位 | 先服务 ~1 万行联调；百万以后再说 |
| 执行模型 | 样本预览 → 创建作业 → 后台 keyset 分批；UI 轮询 |
| 作业存储 | `memory` \| `database` 可切换；**默认 memory**；database **本阶段必须实现** |
| 控制 | 进度 + **暂停 / 继续 / 取消** |
| 行失败 | 记失败明细，继续跑；结束可为 PARTIAL |
| 硬顶 | 默认 `max-rows=20000`，创建时拒绝超限 |
| 旧 apply | UI 改走 jobs 流；`apply.json` 废弃或仅兼容说明 |

---

## 3. 架构

```text
浏览器 /monitor 刷数页
  → preview（样本 + COUNT）
  → create job
  → poll status / pause / resume / cancel
        │
MonitorDispatcher → MonitorOpsFacade
        │
   BatchJobStore (interface)
     ├─ MemoryBatchJobStore   (default)
     └─ JdbcBatchJobStore     (target DB tables)
        │
   BatchWorker (in-process)
     └─ keyset page → compute → SECURT_SKIP UPDATE → advance cursor
```

- Preview **不再**创建可 apply 的全量行快照。  
- Worker 与 HTTP 解耦；暂停/取消在**当前批结束后**落入稳态，避免半批无 cursor。  
- `database` 模式：作业与失败行落在**目标业务库**系统表（与刷数同一 DataSource）。  

---

## 4. 组件

| 组件 | 职责 |
|------|------|
| `BatchJobStore` | 创建/更新作业、追加失败、查询；按配置选实现 |
| `MemoryBatchJobStore` | ConcurrentHashMap + TTL；进程重启丢失 |
| `JdbcBatchJobStore` | 目标库表持久化；ensure DDL（H2 + MySQL）；重启后可 resume |
| `BatchWorker` | 认领 READY/需续跑作业；分批执行；响应 pause/cancel |
| `MonitorOpsFacade` | preview / create / status / pause / resume / cancel |
| Monitor UI | 样本双列预览 → 启动 → 进度与失败摘要 → 控制按钮 |

---

## 5. 状态机

```text
READY → RUNNING ⇄ PAUSING → PAUSED
              ↘ CANCELING → CANCELLED
              ↘ SUCCEEDED | PARTIAL | FAILED

PAUSED → RUNNING   (resume)
PAUSED → CANCELLED (cancel)
```

| 状态 | 含义 |
|------|------|
| READY | 已创建，等待 worker |
| RUNNING | 正在跑批 |
| PAUSING | 已请求暂停，当前批结束后 → PAUSED |
| PAUSED | 已停在合法 cursor，可 resume |
| CANCELING | 已请求取消，当前批结束后 → CANCELLED |
| CANCELLED | 用户取消；已写入行保留 |
| SUCCEEDED | 处理完且 `failedCount == 0` |
| PARTIAL | 处理完且 `failedCount > 0` |
| FAILED | 无法继续（无主键、超硬顶已拒绝不建作业、系统表不可用、致命错误等） |

单行 UPDATE 异常 → 写入失败明细 → **不**因此将整作业标为 FAILED。

---

## 6. 数据模型

### 6.1 作业（逻辑字段）

`jobId`, `datasourceId`, `table`, `op` (`encrypt|decrypt|sign`), `where`, `idColumn`, `fields`,  
`status`, `totalEstimated`, `processed`, `succeeded`, `failed`,  
`cursorPk`, `chunkSize`, `errorMessage`, `createdAt`, `updatedAt`, `expiresAt`（memory TTL）

### 6.2 失败行

`jobId`, `pk`, `message`, `createdAt`  
明细条数设上限（可配置，如每作业最多 1000 条），超出只累加 `failed` 计数。

### 6.3 Database 物理表（建议名）

- `securtkit_monitor_batch_job`  
- `securtkit_monitor_batch_failure`  

首次使用或 Monitor 启动时 `JdbcBatchJobStore.ensureSchema()`；无建表权限则明确报错并提示改用 `memory`。

---

## 7. 数据流（单批）

1. Worker 加载作业（`RUNNING`），读取 `cursorPk` / `where` / `chunkSize`  
2. `SECURT_SKIP SELECT <cols> FROM t WHERE (<where>) AND pk > ? ORDER BY pk LIMIT N`  
3. 对每行在内存计算 after（策略与现 preview 一致：加解密走 `FieldEncryptorStrategy`，sign 走 `DigestService`）  
4. `SECURT_SKIP UPDATE` 按主键写回（JDBC batch 优先）  
5. 更新 `processed` / `succeeded` / `failed` / `cursorPk`  
6. 若无更多行 → `SUCCEEDED` 或 `PARTIAL`；若 pause/cancel 请求在批末生效 → `PAUSED` / `CANCELLED`

Keyset 游标，避免大 OFFSET。本阶段假设单列可排序主键（与现有 `PrimaryKeyResolver` 一致）。

---

## 8. API

| 方法 | 路径 | 作用 |
|------|------|------|
| POST | `/api/batch/preview.json` | 样本（默认 20 行）+ `totalEstimated`；不返回全量 rows 作业 |
| POST | `/api/batch/jobs.json` | 按同样条件创建作业并调度；超 `max-rows` 拒绝 |
| GET | `/api/batch/jobs/{id}.json` | 状态、进度、近期失败 |
| POST | `/api/batch/jobs/{id}/pause.json` | → PAUSING |
| POST | `/api/batch/jobs/{id}/resume.json` | PAUSED → READY/RUNNING |
| POST | `/api/batch/jobs/{id}/cancel.json` | → CANCELING |

旧 `POST /api/batch/apply.json`：文档标记废弃；实现可保留短期兼容或直接返回引导错误。

守卫沿用：`BatchGuard` 表白名单、WHERE 校验、主键解析。

---

## 9. 配置

```yaml
securtkit:
  monitor:
    batch:
      store: memory          # memory | database
      max-rows: 20000
      chunk-size: 200
      sample-size: 20
      max-failure-records: 1000
      memory-ttl-ms: 3600000 # 仅 memory
```

---

## 10. 错误处理

| 场景 | 行为 |
|------|------|
| `COUNT` > max-rows | 创建作业拒绝 |
| 表/主键/WHERE 非法 | preview/create 400 |
| database ensure 失败 | 明确错误；可改 memory |
| 行级 UPDATE 失败 | 记 failure，继续 |
| Worker 线程异常 | 作业 → FAILED，写 `errorMessage` |
| memory 进程重启 | 作业丢失（预期） |
| database 进程重启 | 可对 PAUSED 直接 resume；启动时将泄漏的 `RUNNING`/`PAUSING`/`CANCELING` **统一置为 `PAUSED`**，由用户确认后继续或取消 |

---

## 11. UI 要点

- 预览区只展示样本 before/after + 估计总行数  
- 确认后展示：进度（processed/total）、succeeded/failed、状态  
- 按钮：启动、暂停、继续、取消（按状态启用）  
- 多表：每表独立 job（修复「只留最后一个 jobId」）；本阶段不引入联合作业  

---

## 12. 测试

1. **Store**：memory 状态迁移；jdbc 用 H2 落库与 resume  
2. **Worker**：pause 后 cursor 一致；cancel 后不再写；混合失败 → PARTIAL  
3. **IT（boot2）**：依赖启动补数后的 ~1 万 `user`（或测试内插入），跑一轮 decrypt/encrypt，轮询至终态  
4. **硬顶**：超过 max-rows 创建失败  

---

## 13. 分期与后续

| 阶段 | 内容 |
|------|------|
| 本阶段 | 本文档：异步 + 双 Store + 暂停/继续/取消 + ~1 万验证 |
| 后续 | 百万级、租约/多实例、失败重试单行、database 默认策略调优 |

---

## 14. 与既有设计关系

扩展 [2026-09-06 Monitor 运维台](./2026-09-06-monitor-ops-console-design.md) 中「刷数预览回写」能力：写库路径从「同步全量快照 apply」升级为「异步分批作业」。配置全览、单值工具、SQL 双视图不在本次范围。
