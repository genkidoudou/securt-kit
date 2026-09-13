# Task 4 Report: DigestSqlRewriter（纯函数）

## 状态

已完成 Task 4。新增 `DigestRewriteResult` DTO 与 `DigestSqlRewriter.tryAppendTargets` 纯函数，
支持单表 INSERT（显式列 + VALUES）/ UPDATE 在末尾追加摘要目标列及 `?` 占位符；
多表 JOIN、非 INSERT/UPDATE、解析失败时返回 `rewritten=false` 与 `warnMessage`。

## TDD RED

1. 先创建 `DigestSqlRewriterTest`（appendInsertColumn / appendUpdateSet / skipMultiTable）。
2. RED 命令：
   `mvn -pl securt-kit-core -am "-Dmaven.test.skip=false" "-Dtest=DigestSqlRewriterTest" test`
3. RED 结果：测试编译失败，缺少 `DigestRewriteResult` 与 `DigestSqlRewriter`。

## TDD GREEN

实现内容：

- **DigestRewriteResult**
  - 字段：`sql`、`appendedParameterIndexes`（不可变列表）、`rewritten`、`warnMessage`
  - 包内工厂：`unchanged`、`notRewritten`、`rewritten`
- **DigestSqlRewriter**
  - `CCJSqlParserUtil.parse` 解析 SQL
  - **INSERT**：要求显式列 + `Values`；列尾追加 `Column`，各行 VALUES 追加 `JdbcParameter`；已有列（忽略大小写/引号）跳过
  - **UPDATE**：检测 `startJoins` 与 `joins`（MySQL `UPDATE t JOIN ...` 语法走 `startJoins`）；SET 尾追加 `UpdateSet(col, ?)`；索引 = 原 SET 占位符数 + 1（1-based）
  - 批量 VALUES 结构（嵌套 `ExpressionList`）与单行 VALUES 均支持

GREEN 命令：
`mvn -pl securt-kit-core -am "-Dmaven.test.skip=false" "-Dtest=DigestSqlRewriterTest" test`

结果：**3 tests，0 failures，BUILD SUCCESS**。

## 参数索引

简报断言与 jsqlparser 改写结果一致，**未修改测试**：

| 场景 | 原 SQL | 追加索引 |
|------|--------|----------|
| INSERT | `(?, ?)` VALUES | `[3]` |
| UPDATE | SET 1 个 `?`，WHERE 1 个 `?` | `[2]`（新 `?` 在 SET 末、WHERE 前） |

## 注意事项

- PowerShell 需对 `-Dmaven.test.skip=false` 加引号。
- 多表 UPDATE 须同时检查 `Update.getStartJoins()` 与 `getJoins()`。
- INSERT SELECT、无显式列、空 `missingTargetFields` 不改写。
- 尚未接入 JDBC；Task 5+ 再 wiring。

## Commit

```
feat(digest): add single-table SQL rewriter for digest columns
```

仅包含 Task 4 三个文件。
