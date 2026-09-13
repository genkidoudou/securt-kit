## Why

SELECT 投影里用 `IFNULL(col,'x')` / `TRIM(col)` / `CAST(...)` 等包裹加密列时，解析不登记待解密字段，结果列标签也对不上配置字段名，导致 JDBC、MYBATIS、Monitor 均不解密返回值。业务常见「空则默认」写法因此失效，需要与 mode 无关的结果侧补齐。

## What Changes

- SELECT 表达式 visitor 递归进入函数/CAST/TRIM 等，抽出源列并登记 `FieldEncryptorInfoDto`（多加密列取第一个）
- 无别名时用规范化表达式作结果匹配键；JDBC 增加列下标回退
- JDBC `ResultSetDecryptingProxy` 与 MYBATIS `ResultDecryptHelper` 按新匹配规则解密函数返回值
- Monitor SQL 双视图接入同一解析映射后再解密 `plainRows`
- 文档区分「库内明文语义函数」与「结果侧解密函数返回值」

## Capabilities

### New Capabilities
- `select-function-decrypt`: SELECT 函数/CAST 等包裹加密列时的结果列登记与跨通道解密行为（含多列取第一、别名/表达式/下标匹配、失败策略与边界）

### Modified Capabilities
- （无；`openspec/specs/` 下尚无已归档的同名能力主规格，本变更以新能力 delta 引入）

## Impact

- **代码**：`securt-kit-core`（解析 visitor、`FieldEncryptorInfoDto`、`ResultSetDecryptingProxy`）、`securt-kit-mybatis`（`ResultDecryptHelper`）、`securt-kit-monitor`（`MonitorResultDecryptor` / `querySql`、帮助文案）
- **行为**：函数投影列在返回值仍为密文时可解密；`UPPER(密文)` 等可能 FALLBACK；`CONCAT` 多加密列仅按第一个策略试解
- **文档**：`CAPABILITY-GAPS.md`、`MYBATIS-MODE-DESIGN.md`、Monitor 帮助
- **非破坏**：不改 SQL、不改 mode 开关；裸列 SELECT 行为保持
