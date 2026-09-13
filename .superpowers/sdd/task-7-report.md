# Task 7 Report

## Status

完成 JDBC ResultSet 读路径可选验签。

## Implementation

- 新增公共 `DigestReadSupport`，汇总启用读验签规则所需列并调用 `DigestService.verifyRow`。
- `ResultSetDecryptingProxy` 在 `next()` 成功后重置行状态，在首次列读取前收集所需列、解密源字段并执行一次验签。
- 验签发生在调用方读取前，因此内部预读不会破坏当前 getter 对 `ResultSet.wasNull()` 的语义。
- `FALLBACK` 不阻断读取；`FAIL_FAST` 传播 `DigestMismatchException`。

## Tests

- RED：`DigestReadSupportTest` 因缺少 `DigestReadSupport` 编译失败。
- GREEN：`mvn -pl securt-kit-core -am "-Dmaven.test.skip=false" "-Dtest=DigestReadSupportTest" test`，2 tests passed。
- 回归：`mvn -pl securt-kit-core -am "-Dmaven.test.skip=false" test`，33 tests passed。

## Commit

`b62cb99 feat(digest): optional verify-on-read for JDBC ResultSet`

提交仅包含任务指定的 3 个文件。`ResultSetDecryptingProxy` 原有未提交 WIP 已恢复并保持未暂存。

## Concerns

- 第一期仍不保证 JOIN / 多表同名列验签语义，与设计非目标一致。
- IDE 仍报告代理中既有未使用字段/方法警告；本任务未扩大范围处理。
