# 字段完整性摘要（Digest）设计方案

> 状态：**已实现 P0/P1**（P2 边界增强持续进行；实现计划：`docs/superpowers/plans/2026-09-05-field-digest-integrity.md`）  
> 版本：v1.0  
> 日期：2026-09-05  
> 目的：在配置字段加密的同时，对指定源字段的明文计算完整性摘要并落库，可选在读取时验签，用于检测库侧篡改。

**非目标（与本方案区分）**

- 不是盲索引 / 等值检索用的 `search.type: HASH`（见 `docs/MYBATIS-MODE-DESIGN.md` 后续规划）
- 不做跨表 JOIN 结果验签、批量 `ExecutorType.BATCH` 完整保证、监控台试算 UI（第一期不做）

---

## 1. 背景与目标

### 1.1 问题

字段加密后，攻击者或误操作仍可能直接改密文列。业务侧需要一种与加密策略协同的**完整性校验**能力：写入时根据明文生成摘要列；读取时可选择验签。

### 1.2 目标

1. **完整性**：摘要输入为**明文**（写前 / 读后解密后），检测库侧对密文或摘要列的篡改  
2. **配置驱动**：表级 `digest` 列表，支持多组源字段 → 一个目标摘要列  
3. **双通道**：JDBC（`mode=JDBC`）与 MYBATIS（`mode=MYBATIS`）共用同一套配置与 `DigestService`  
4. **低侵入写入**：单表 INSERT/UPDATE 若未带 `target-field`，框架可改写 SQL 追加该列（不必业务每次手写）  
5. **部分 UPDATE 可配**：`SKIP | RELOAD | FAIL`，默认 `RELOAD`

### 1.3 非目标（第一期）

| 项 | 说明 |
|----|------|
| JOIN / 多表验签 | 解析与源字段归属复杂，不做 |
| BATCH 完整语义 | 不保证 batch 路径下补读/改写完整 |
| 监控试算 UI | 后续可加 |
| 独立 `DigestStrategy` 接口 | 采用扩展 `FieldEncryptorStrategy`（方案 1） |

---

## 2. 决策摘要

| 议题 | 决策 |
|------|------|
| 用途 | 完整性（防篡改），非检索盲索引 |
| 摘要输入 | 明文（encrypt 前 / decrypt 后） |
| 写 / 读 | 始终写摘要；读侧验签可选（默认关） |
| 策略放置 | 扩展 `FieldEncryptorStrategy`：`digest` + `verifyDigest`（default 方法，Java 8 兼容） |
| 配置形态 | 表级 `digest:` 列表：`source-fields` / `target-field` / 可选覆盖项 |
| 全局默认 | `digest-strategy`、`digest-partial-update`、`digest-verify-on-read` |
| 部分 UPDATE | 默认 `RELOAD`（缺源字段则从 DB 补读、解密、重算） |
| 通道 | JDBC + MYBATIS |
| 缺 target 列 | 框架可改写**单表** INSERT/UPDATE 追加列；多表 / 解析失败 → warn，不改写 |

---

## 3. 配置设计

### 3.1 示例

```yaml
securtkit:
  encryptor:
    # 全局摘要默认（均可被表级 digest 项覆盖）
    digest-strategy: io.github.hexlodev.core.strategy.HmacSha256DigestStrategy  # 示例类名，实现阶段定名
    digest-partial-update: RELOAD   # SKIP | RELOAD | FAIL
    digest-verify-on-read: false
    # 可选：验签失败策略；未配则沿用 failure-policy
    # digest-failure-policy: FALLBACK

    tables:
      - table-name: user
        fields:
          - field-name: phone
            strategy: ...
          - field-name: id_card
            strategy: ...
        digest:
          - source-fields: [phone, id_card]
            target-field: row_digest
            # 以下可选，缺省用全局
            # strategy: ...
            # partial-update: RELOAD
            # verify-on-read: false
            # failure-policy: FALLBACK
```

### 3.2 模型要点

- `source-fields`：有序列表；拼入 `LinkedHashMap` 时保持配置顺序，保证摘要确定性  
- `target-field`：摘要落库列名；**不应**再出现在同表 `fields` 加密清单中（启动校验）  
- `strategy`：解析为 `FieldEncryptorStrategy` 实例（与加密字段共用 `StrategyCache`）  
- 配置了 `digest` 但策略未覆盖 `digest`（default 仍返回 null）→ **启动失败**  
- 多组 `digest`：同一表可多条；`target-field` 不可重复  

### 3.3 `partial-update` 语义

| 值 | 行为 |
|----|------|
| `SKIP` | 本次 UPDATE 未覆盖全部 `source-fields` 时，不更新该组摘要 |
| `RELOAD`（默认） | 缺的源字段从当前行 DB 补读 → 按字段策略解密 → 与本次明文合并 → 重算 |
| `FAIL` | 缺任一源字段则抛配置/运行时错误，中断写入 |

**INSERT 无旧行**：`RELOAD` 无法补读 → 与 `FAIL` 同等处理（缺源则失败或按策略明确报错）。

---

## 4. 策略接口扩展

在现有 `FieldEncryptorStrategy` 上增加 default 方法（Java 8）：

```java
/**
 * 对有序源字段明文计算摘要。未实现摘要的策略保持返回 null。
 * 若某 DigestConfig 引用了该策略且返回 null，启动校验失败。
 */
default String digest(Map<String, String> sourcePlainValues) {
    return null;
}

/**
 * 校验摘要是否与明文一致。默认：重新 digest 后常量时间比较。
 */
default boolean verifyDigest(Map<String, String> sourcePlainValues, String digestValue) {
    if (digestValue == null || sourcePlainValues == null) {
        return false;
    }
    String expected = digest(sourcePlainValues);
    if (expected == null) {
        return false;
    }
    return constantTimeEquals(expected, digestValue);
}
```

**约定**

- Map 的 key 为字段名，value 为明文；顺序由调用方按 `source-fields` 构造  
- 推荐内置实现：HMAC-SHA256（密钥来自策略构造 / 配置），输出 Base64 或 hex（实现阶段统一）  
- 仅做加密、不做摘要的策略可不覆盖；被 digest 引用时必须覆盖  

---

## 5. 写入流程

```text
解析 SQL / 参数
  → 识别涉及表的 DigestConfig
  → 收集 source-fields 明文（来自绑定参数 / 实体 / Wrapper）
  → 按 partial-update 处理缺字段（SKIP / RELOAD / FAIL）
  → strategy.digest(map) → 得到摘要字符串
  → 若 SQL 未含 target-field：单表则可改写 INSERT/UPDATE 追加列与占位符并绑定；否则 warn
  → 再走现有字段加密（encrypt）
  → 执行
```

**顺序**：先算摘要（明文），再加密字段。摘要列本身不加密。

**改写范围**

- 允许：单表 INSERT、单表 UPDATE  
- 不允许：多表、解析失败、无法安全定位 SET/VALUES → 仅 warn，不改写；此时若业务未带 `target-field`，摘要无法落库（与「业务必须带列」等价降级）

**通道**

- JDBC：`ParameterEncryptor` / Statement 代理路径接入 `DigestService`  
- MYBATIS：`ParameterEncryptHelper`（含 MP Wrapper）接入同一门面  

---

## 6. 读取可选验签

### 6.1 开关

- 全局 `digest-verify-on-read` 默认 `false`  
- 表级 `digest[].verify-on-read` 可覆盖  

### 6.2 时机

```text
结果集 / 映射结果
  → 字段解密得到明文
  → 若开启验签
  → 取 source 明文 + target 摘要列
  → verifyDigest
  → 按失败策略处理
```

### 6.3 验签失败策略

与现有 `failure-policy` 对齐；可另配 `digest-failure-policy`（未配则跟全局 `failure-policy`）。

| 策略 | 行为 |
|------|------|
| `FALLBACK`（默认） | warn，仍返回解密后业务数据 |
| `FAIL_FAST` | 抛 `DigestMismatchException`，中断本次读取映射 |
| `SKIP` | 跳过该行/该摘要组校验，不抛错 |

（`RETRY` 对摘要无意义，可忽略或等同 FALLBACK。）

### 6.4 边界

| 情况 | 行为 |
|------|------|
| 结果缺 `target-field` 或任一 `source-field` | 无法验签 → debug；仅当严格 `FAIL_FAST` 且产品后续加「严格模式」再失败（第一期：不当失败） |
| 摘要列为 null | 视为未初始化（迁移期）→ warn 一次，不阻断 |
| JDBC | `ResultSetDecryptingProxy` 路径 |
| MYBATIS | `ResultDecryptHelper` 路径 |

---

## 7. 架构与模块落点

```text
FieldEncryptorProperties (+ DigestConfig)
        │
TableConfigRegistry / 启动校验
        │
DigestService（门面：计算、补读、验签、失败策略）
        │
   ┌────┴────┐
 JDBC 写/读   MYBATIS 写/读
```

| 模块 | 职责 |
|------|------|
| `securt-kit-core` | 配置模型、策略扩展、`DigestService`、JDBC 改写与验签、启动校验、异常类型 |
| `securt-kit-mybatis` | Parameter / Result 路径调用同一门面 |
| starter-boot2/3 | 绑定新配置项 |
| 文档 | `USAGE`、本 spec、必要时 INDEX 链接 |

**RELOAD 补读**：由 `DigestService` 使用当前连接按主键（或 WHERE 可解析的唯一条件）查询缺失列；无法解析主键/条件时按 `FAIL` 处理并打明确错误。

---

## 8. 启动校验清单

1. `target-field` 不在同表加密 `fields` 中  
2. `source-fields` 非空；字段名合法；建议源字段均在 `fields` 中（若有未加密源字段，允许但文档说明风险）  
3. 同一表 `target-field` 不重复  
4. 解析得到的策略 `digest(...)` 在空探测或标记接口上不得对「被引用策略」返回 null（实现阶段可用启动探针或 `supportsDigest()`）  
5. `partial-update` / `verify-on-read` 枚举合法  

---

## 9. 错误与日志

| 场景 | 处理 |
|------|------|
| 摘要计算抛错 | 走 `digest-failure-policy` / `failure-policy` |
| 验签不匹配 | 见 §6.3 |
| SQL 无法改写且未带 target | warn：摘要未写入 |
| RELOAD 无法定位行 | error / FAIL |
| 策略返回 null（运行时兜底） | 视为配置错误，FAIL_FAST 语义 |

新增异常：`DigestMismatchException`、必要时 `DigestConfigurationException`（可归入现有 `ConfigurationException`）。

---

## 10. 测试范围

### 10.1 单测（core / mybatis）

- `digest` / `verifyDigest` 确定性与篡改失败  
- 全局 vs 表级覆盖  
- `partial-update`：SKIP / RELOAD / FAIL  
- 单表 INSERT/UPDATE SQL 追加 `target-field`  
- 多表不改写  

### 10.2 集成（boot2 / boot3）

- INSERT 自动带摘要列并落库  
- UPDATE 部分字段 + RELOAD 后摘要正确  
- `verify-on-read=true`：篡改密文或摘要列 → FALLBACK warn / FAIL_FAST 抛错  
- JDBC 与 MYBATIS 各至少一条 happy path  

### 10.3 第一期不测 / 不保证

- JOIN 结果验签  
- MyBatis BATCH  
- 监控 UI  

---

## 11. 实现分期建议

| 阶段 | 内容 |
|------|------|
| P0 | 配置 + 策略扩展 + DigestService + JDBC 写（含单表改写）+ 单测 |
| P1 | JDBC 可选验签 + MYBATIS 写/读接驳 + boot 集成测 |
| P2 | RELOAD 健壮性（主键解析边界）、文档与 USAGE、失败策略打磨 |

---

## 12. 自检记录

- [x] 与头脑风暴决策表一致（明文、方案 1、RELOAD 默认、双通道、SQL 可改写）  
- [x] 与盲索引 HASH 检索明确区分  
- [x] 写在加密前、读在解密后  
- [x] INSERT + RELOAD 边界已写明  
- [x] 第一期非目标已列出  
- [x] 未包含实现代码；待用户审阅 spec 后再进入 writing-plans  

---

## 13. 待实现前确认（审阅清单）

请确认无异议后进入实现计划：

1. 配置 YAML 与覆盖规则  
2. `FieldEncryptorStrategy` 扩展方式  
3. 写入顺序与单表 SQL 改写  
4. 读侧可选验签与失败策略  
5. 模块落点与分期

