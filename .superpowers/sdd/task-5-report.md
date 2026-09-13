# Task 5 Report: DigestService

## Status

完成。

## Implemented

- 新增 `DigestService`，支持按配置顺序、忽略列名大小写计算摘要。
- 实现 partial-update 的 `SKIP`、`FAIL`、`RELOAD`；INSERT 缺源字段时 RELOAD 明确失败。
- RELOAD 支持使用调用方 Connection/WHERE 补读，并通过 `FieldCryptoService` 解密补读值。
- 实现读侧验签及 `FALLBACK`、`RETRY`、`SKIP`、`FAIL_FAST` 策略。
- 新增 `DigestMismatchException`。

## TDD Evidence

- RED：`DigestServiceTest` 首次编译失败，缺少 `DigestService` 和 `DigestMismatchException`。
- GREEN：定向测试 10/10 通过。
- 回归：core 模块测试 25/25 通过。

## Verification

```text
mvn -pl securt-kit-core -am "-Dmaven.test.skip=false" "-Dtest=DigestServiceTest" test
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0

mvn -pl securt-kit-core -am "-Dmaven.test.skip=false" test
Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
```

## Concerns

- 按任务许可，RELOAD 单测只覆盖缺少 Connection/WHERE 的失败路径；成功补读路径留待后续数据库集成测试。
- Maven 输出现有 SLF4J NOP binding 提示，不影响测试结果。
