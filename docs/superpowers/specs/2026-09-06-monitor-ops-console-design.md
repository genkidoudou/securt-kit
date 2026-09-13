# Monitor 运维台增强设计方案

> 状态：**已实现**  
> 版本：v1.0  
> 日期：2026-09-06  
> 目的：扩展 `/monitor` 为完整运维台——配置全览、单值/单行加解密与验签、表+WHERE 刷数（预览后回写）、SELECT 双视图与参数加密 SQL；与 `/playground` 职责分离。

---

## 1. 背景与目标

### 1.1 现状

| 能力 | 现状 |
|------|------|
| `/monitor` | 字符串加解密、SQL 解析、参数加密、SQL 查询、数据初始化、部分配置（表/字段/加密策略） |
| 配置缺口 | 未完整展示 mode、digest 规则、verify-on-read、digest 策略、skip-comment 等 |
| 摘要运维 | 无单行验签、无按 WHERE 批量签名/加解密预览回写 |
| SQL | 有查询能力，但无「同次密文 + 明文」双视图强化 |
| `/playground` | 业务表演示（CRUD/Digest/Complex），非运维刷数台 |

### 1.2 目标

1. **配置全览**：加密表/字段/策略 + 摘要验签表/字段/策略 + 全局相关开关  
2. **刷数作业**：表 + WHERE → 预览加解密/签名结果 → 确认后按主键 UPDATE  
3. **单行验签**：表名 + id（主键列可解析/临时输入）  
4. **单值工具**：加密、解密、生成签名、验签  
5. **SQL**：仅 SELECT；密文视图 + 解密视图；保留/强化参数加密后 SQL  
6. **可扩展 backlog**（非第一期）：审计日志、导出变更 SQL、任意 DML 等  

### 1.3 非目标（第一期）

- 任意 DML / 自由 UPDATE-DELETE 执行器  
- 配置热写回 YAML 文件  
- 与 playground 合并为单一巨型 UI  
- 强制依赖 MyBatis-Plus（无 MP 时跳过 TableInfo）  

---

## 2. 决策摘要

| 议题 | 决策 |
|------|------|
| 落点 | 扩展现有 `securt-kit-monitor`（方案 1），不新建 ops 子模块 |
| 分期 | **A → C → B → D**（配置 → 单值/单行 → 刷数预览回写 → SELECT 双视图） |
| 刷数写库 | **预览 → 二次确认 → 按主键 apply** |
| SQL 执行 | 第一期 **仅 SELECT** |
| 主键解析 | **配置 → TableInfo → 页面临时输入**；临时输入仅当前操作，不落盘 |
| 与 playground | 并存：monitor=运维，playground=演示 |

---

## 3. 架构与信息架构

继续使用：

```text
浏览器 /monitor/*
  → MonitorStatViewServlet (boot2/boot3)
  → MonitorDispatcher
  → MonitorEngine (+ 新增 Batch/Row/Digest helpers)
  → 静态资源 support/http/resources/
```

### 3.1 Tab 规划

| Tab | 阶段 | 说明 |
|-----|------|------|
| 配置全览 | A | 增强现有「配置信息」 |
| 加密解密 | C | 单值 encrypt/decrypt/sign/verify |
| 单行验签 | C | 表 + id |
| 刷数作业 | B | 表 + WHERE → preview → apply |
| SQL 解析 | 已有 | 保留 |
| SQL 参数加密 | 已有 + D | 强化展示 |
| SQL 查询 | D | 仅 SELECT；cipherRows + plainRows |
| 数据初始化 | 已有 | 保留 |

### 3.2 主键解析

顺序固定：

1. `securtkit.monitor.table-primary-keys`（`Map<tableName, idColumn>`）  
2. MyBatis-Plus `TableInfo`（classpath 存在时反射/可选依赖获取）  
3. 请求体 / 页面临时输入的 `idColumn`（**不写入配置**）  

页面展示时应显示当前解析来源：`config` | `tableInfo` | `manual` | `none`。

---

## 4. API 与数据流

均挂在 `/monitor/api/*`，需已登录（与现网一致）。

### 4.1 A — 配置全览

**增强** `GET /api/config.json`：

- 全局：`enable`、`mode`、`failurePolicy`、`ignoreTableCase`、`sqlParseCache`、`skipComment`  
- digest 全局：`digestStrategy`、`digestPartialUpdate`、`digestVerifyOnRead`、`digestFailurePolicy`、`digestHmacKeyConfigured`（布尔，**不回显密钥**）  
- 每表：`datasourceId`、`fields[]`（fieldName / strategy）、`digest[]`（sourceFields / targetField / 策略覆盖 / verifyOnRead 覆盖）  
- 可选附带每表 `primaryKey: { column, source }`  

**可选** `GET /api/primary-key.json?table=&datasourceId=` → `{ column, source }`。

### 4.2 C — 单值工具 + 单行验签

- 保留 `POST /api/encrypt.json`、`POST /api/decrypt.json`  
- **新增** `POST /api/digest.json`：`action=sign|verify`；入参明文 map 或表字段上下文 +（verify 时）期望摘要  
- **新增** `POST /api/row/verify.json`：`table`、`id`、可选 `idColumn`、`datasourceId`  
  → 按主键查一行；返回 `verified`、解密后字段、库中摘要、期望摘要或失败原因  
  → monitor 默认以 JSON 业务结果返回，不因 FAIL_FAST 拖垮整个页面会话  

### 4.3 B — 刷数预览与回写

- `POST /api/batch/preview.json`  
  - 入参：`table`、`where`（绑定参数或受控条件）、`op=encrypt|decrypt|sign`、可选字段列表、`datasourceId`、可选 `idColumn`  
  - 出参：`jobId`、行列表（`pk`、当前值、拟写入值、摘要变化）、`expiresAt`  
  - **不写库**  

- `POST /api/batch/apply.json`  
  - 入参：`jobId`（或显式变更列表 + 校验令牌）  
  - 行为：按主键逐行 UPDATE；单次 ≤ **500** 行  
  - 部分失败：`partialSuccess` + 失败明细  

**WHERE 约束：** 禁止多语句与危险注入形态；默认仅允许已配置加密或摘要的表（可配置放宽）。空 WHERE 默认拒绝（可配置放开，不推荐）。

### 4.4 D — SELECT 双视图 + 参数加密 SQL

- 增强 `POST /api/query-sql.json`：  
  - 仅允许 SELECT  
  - 返回 `{ columns, cipherRows, plainRows, sql }`  
  - **cipher**：经 skip-comment（或等价跳过解密通道）读取  
  - **plain**：正常拦截连接读取（自动解密）  
  - 若 `skip-comment` 未启用且无法取密文 → 明确错误，不静默只返回明文  

- 保留并强化 `POST /api/encrypt-sql.json` 对「参数加密后 SQL」的展示。

---

## 5. 错误处理与限制

| 场景 | 行为 |
|------|------|
| 表不在允许范围 | 400 |
| 主键无法解析且未输入 | 400，提示配置或临时填写 |
| WHERE 非法 | 400 |
| preview 过期 / jobId 无效 | 400，要求重新预览 |
| apply 部分失败 | 200 + partialSuccess + 失败列表 |
| 非 SELECT | 400 |
| 无法提供密文视图 | 明确要求开启 skip-comment 或说明原因 |

**硬约束：** 刷数必须 preview→apply；apply ≤500 行；SQL 仅 SELECT；密钥脱敏；MP 可选。

---

## 6. 配置增量

```yaml
securtkit:
  monitor:
    enabled: true
    path: /monitor
    # 主键兜底（优先于 TableInfo）
    table-primary-keys:
      user: id
      digest_user: id
    # 可选：刷数允许空 WHERE（默认 false）
    # batch-allow-empty-where: false
    # 可选：刷数表白名单放宽（默认仅已配置加密/摘要表）
    # batch-allow-unconfigured-tables: false
```

---

## 7. 实现分期

| 阶段 | 内容 | 验收要点 |
|------|------|----------|
| A | config.json 全量字段 + UI 配置全览 | 页面可见 digest 规则与全局开关；密钥不回显 |
| C | digest.json + row/verify + 加密页签增强 | 单值 sign/verify；表+id 验签 |
| B | batch preview/apply + 刷数 Tab | 预览对照 → 确认回写；超限/无 PK 拒绝 |
| D | query-sql 双视图 + encrypt-sql 展示强化 | SELECT 同屏密文/明文；非 SELECT 拒绝 |

P3 backlog：操作审计、导出 apply SQL、受控 DML、多 DS 对照（非本期）。

---

## 8. 测试计划

- **单元**：config DTO 含 digest；PK 优先级；WHERE 多语句拒绝；非 SELECT 拒绝  
- **集成（boot2）**：preview/apply 一轮；row/verify；query-sql 双视图（需 skip-comment）  
- **手工冒烟**：各新 Tab 走通；与 `/playground` 互不影响  

---

## 9. 自检记录

- [x] 与头脑风暴决策一致（方案 1 + A→C→B→D + 预览回写 + 仅 SELECT + PK 顺序）  
- [x] 与 playground 边界清晰  
- [x] 无 TBD/TODO 占位；密钥脱敏写明  
- [x] 第一期范围可单独立项实现；P3 已拆出  
- [x] 未包含实现代码  

---

## 10. 审阅清单

1. Tab 与分期是否符合运维使用习惯  
2. preview/apply 与 500 行上限是否合适  
3. SELECT 双视图对 skip-comment 的依赖是否可接受  
4. 主键「配置优先于 TableInfo」是否与业务配置习惯一致  
