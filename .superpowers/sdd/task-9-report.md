# Task 9 Report

## Status

已完成并提交 Boot2 Digest JDBC 集成测试、测试配置/schema、USAGE 配置示例及设计状态更新。

## Commit

- `676b3d3 test(digest): add boot2 IT and document digest configuration`
- 提交仅包含：
  - `securt-kit-test-boot2/src/test/java/io/github/test/DigestIntegrityIT.java`
  - `securt-kit-test-boot2/src/main/resources/application.yml`
  - `securt-kit-test-boot2/src/main/resources/schema.sql`
  - `docs/USAGE.md`
  - `docs/superpowers/specs/2026-09-05-field-digest-integrity-design.md`

## Tests

通过：

```text
mvn -pl securt-kit-core,securt-kit-mybatis,securt-kit-starter-boot2 -am "-Dmaven.test.skip=true" install
mvn -pl securt-kit-test-boot2 "-Dmaven.test.skip=false" "-Dtest=DigestIntegrityIT" test
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
```

覆盖：

1. INSERT 未提供 `row_digest` 时由 JDBC 拦截路径自动补列并写入 HMAC 摘要。
2. 仅 UPDATE `phone` 时按 `RELOAD` 配置重算摘要。
3. 原生 JDBC 篡改 `row_digest` 后，拦截查询按 `FAIL_FAST` 抛出 `DigestMismatchException`。

## Concerns

任务简报指定的 reactor 命令：

```text
mvn -pl securt-kit-test-boot2 -am "-Dmaven.test.skip=false" "-Dtest=DigestIntegrityIT" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

会在执行目标用例前被现有测试编译错误阻塞：`SqlParseErrorTest`、`SqlParseCacheTest`、`TextTypeInterceptionTest`、`SecurtkitUtilsParseSqlTest` 等仍引用仅在 core `package` 阶段生成的 `io.github.hexlodev.shaded.jsqlparser` 包。为避免修改或提交非 Task 9 文件，先安装 shaded 依赖，再单独运行 Boot2 模块，目标 IT 已通过。

测试使用专用 `digest_user` 表，避免 H2 保留字 `"user"` 在 UPDATE 表名解析时保留引号而无法命中规则；公共 schema 中的 `"user"` 仍按要求增加了 `row_digest`。
