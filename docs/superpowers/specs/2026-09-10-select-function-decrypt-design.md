# SELECT 函数包裹加密列结果解密设计

> 状态：已实现  
> 版本：v1.0  
> 日期：2026-09-10  
> 目的：使 `IFNULL(col,'x')` / `TRIM(col)` / `CAST(col AS …)` 等 SELECT 投影在 JDBC、MYBATIS、Monitor 下能按源加密字段解密返回值。  
> 关系：补强 [MYBATIS-MODE-DESIGN.md](../../MYBATIS-MODE-DESIGN.md) §8 与 [CAPABILITY-GAPS.md](../../CAPABILITY-GAPS.md) 中「函数列结果不解密」缺口；不改变「库内对密文做明文语义函数」的非目标。

---

## 1. 背景与问题

加密列在 SELECT 中被函数包裹时（例如 `SELECT IFNULL(CUSTOMER_PHONE,'2') FROM orders`）：

1. `FieldParseParseExpressionVisitor.visit(Function)` 等为空实现，不登记 `FieldEncryptorInfoDto`。  
2. 结果列标签多为整段表达式，无法与配置字段名 `CUSTOMER_PHONE` 直接相等匹配。  
3. JDBC 与 MYBATIS 共用该解析结果，故两种 mode 下均不解密；Monitor 后解密也只按「label == 配置字段名」匹配，同样失败。

根因在 **SELECT 投影解析与结果列匹配**，与 mode 开关无关。

---

## 2. 决策摘要

| 议题 | 决策 |
|------|------|
| 函数范围 | 任意可递归进参的函数/CAST/TRIM 等；能抽出加密源列即登记（方案 B） |
| 多加密列 | 同一 SelectItem 内取**第一个**加密源列的策略解密（用户确认 B） |
| 无 AS 别名 | 别名优先；否则规范化表达式字符串作 `columnName`；JDBC 另用列下标回退（方案 C） |
| 通道 | core 解析共用 + JDBC + MYBATIS + Monitor（方案 C） |
| 实现路径 | 只补 SELECT 解析与匹配，**不改写用户 SQL**（方案 1） |
| 语义 | 解密的是函数**返回值**；改写密文导致解失败时走现有失败策略 |

---

## 3. 目标与非目标

### 3.1 目标

1. `SELECT IFNULL(encrypted_col, '2')`（有无 `AS`）在 JDBC / MYBATIS 结果中得到明文（列非空为密文时）；默认字面量命中时保留字面量。  
2. `TRIM` / `CAST` 等抽出唯一（或第一个）加密列时可解密。  
3. Monitor SQL 双视图 `plainRows` 对上述投影列同样解密。  
4. 裸列 SELECT 回归行为不变。

### 3.2 非目标

1. 不在库内实现「对明文」的 `UPPER` / `LIKE` / `SUBSTRING` 语义。  
2. 不改写或重写用户 SQL。  
3. 不处理 `SELECT *` 展开中的隐式函数。  
4. 不为 `CONCAT(phone, id_card)` 提供「双列各自正确」的语义（仅按第一个加密列试解）。

---

## 4. 解析规则（core）

落点：`FieldParseParseExpressionVisitor`（及必要时同类空实现的 CAST/TRIM visitor）。

1. 对 `Function` / `TrimFunction` / `CastExpression` / `TranscodingFunction` 等：递归访问参数，收集可解析的 `Column` → `sourceTable` + `sourceColumn`。  
2. 同一 SelectItem 下若收集到 ≥1 个源列：取**第一个**写入 `FieldInfoDto`（进而进入 `FieldEncryptorInfoDto`）。  
3. `columnName`（结果匹配键）：  
   - 有 `AS` → 使用别名；  
   - 无别名 → 该 SelectItem 表达式的规范化字符串（压缩空白、匹配时忽略大小写）。  
4. `FieldEncryptorInfoDto` 增加可选 `resultColumnIndex`（1-based，与 ResultSet 列序一致），供 JDBC 下标回退。若实现阶段发现维护成本过高，可用「解析列表与 SELECT 投影顺序一致」作为弱替代，但优先显式 index。  
5. 字面量参数（如 `IFNULL` 的默认值）不单独登记为加密字段。

占位符参数侧（WHERE 中函数包列）已有既有能力，本变更**不要求**改写 WHERE 行为，但 SELECT 侧递归抽取应与现有「函数内取列」风格一致，避免两套规则。

---

## 5. JDBC / MYBATIS 解密匹配

### 5.1 JDBC（`ResultSetDecryptingProxy`）

匹配顺序：

1. `columnLabel` / `columnName` 忽略大小写命中 `FieldEncryptorInfoDto.columnName`。  
2. 双方去掉空白后再比一次（驱动 label 与 JSQLParser `toString()` 空格差异）。  
3. 仍失败且存在 `resultColumnIndex`：按列下标取对应 DTO，用其 `sourceTableName` / `sourceColumn` 解密。  
4. 保留现有 `表.列` MetaData 回退。  
5. 统一经 `FieldCryptoService`；失败策略不变（例如 `IFNULL` 落到明文 `'2'` 时解失败 → 保留原值）。

### 5.2 MYBATIS（`ResultDecryptHelper`）

1. **实体**：在现有候选名上继续依赖源列名 / 驼峰属性；`columnName` 为表达式时主要靠源列属性命中。  
2. **Map**：索引增加规范化 / 去空白后的 `columnName`，以命中驱动返回的表达式 label。  
3. 不对实体做列下标解密（无稳定 ordinal API）；无属性或 Map key 则跳过。

---

## 6. Monitor

在现有「raw 读 → `cipherRows` → 后解密 `plainRows`」上：

1. 对 SELECT 使用与 core 相同的字段解析，得到「结果列 label/表达式 → 源表.源列」。  
2. `MonitorResultDecryptor`（或 `querySql` 调用处）优先用该映射解密；再回退「label 等于配置字段名」。  
3. 单行验签路径不因函数投影单独改写：仍对配置摘要源字段解密后验签。  
4. 帮助/项目文档补一句：函数列按源加密字段解返回值；改写密文的函数可能解失败。

---

## 7. 测试

| 用例 | 期望 |
|------|------|
| `IFNULL(phone,'2')` 无别名 | JDBC / MYBATIS / Monitor plain 为明文（非空行） |
| 同上 `AS phone` | 同样解密 |
| `TRIM(phone)` / `CAST(phone AS …)`（库支持的写法） | 能抽出列则解 |
| `CONCAT(phone, id_card)` | 使用第一个加密列策略；失败则 FALLBACK 原值 |
| `IFNULL` 命中默认字面量 | 保留 `'2'` |
| 裸列 `SELECT phone` | 回归不变 |

单元测试优先落在 core 解析 + JDBC/MYBATIS/Monitor 现有测试模块中，用可预测策略（如前缀策略）断言。

---

## 8. 文档

- 更新 `docs/CAPABILITY-GAPS.md`：标明 SELECT 函数投影结果侧可解密及多列取第一的限制。  
- 更新 `docs/MYBATIS-MODE-DESIGN.md` §8：区分「库内明文语义函数」与「结果侧解密函数返回值」。  
- Monitor 帮助文案与上述一致。

---

## 9. 风险与缓解

| 风险 | 缓解 |
|------|------|
| 驱动 columnLabel ≠ parser 表达式字符串 | 规范化空白 + 忽略大小写；JDBC 列下标兜底 |
| `UPPER(密文)` 等解失败 | 失败策略 FALLBACK；文档写明边界 |
| 多加密列 CONCAT 误解 | 固定取第一个；测试锁定行为；文档说明 |

---

## 10. 实现落点（预期）

| 模块 | 变更 |
|------|------|
| `securt-kit-core` | 表达式 visitor 递归；`FieldEncryptorInfoDto` 可选 index；`ResultSetDecryptingProxy` 匹配增强 |
| `securt-kit-mybatis` | `ResultDecryptHelper` 索引/候选名兼容表达式 label |
| `securt-kit-monitor` | 解析映射接入 `MonitorResultDecryptor` / `querySql`；帮助文案 |
| `docs` | CAPABILITY-GAPS、MYBATIS-MODE-DESIGN、必要时 Monitor 帮助 |
