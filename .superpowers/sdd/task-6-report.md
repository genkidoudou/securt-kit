# Task 6 Report: JDBC 写路径摘要接入

## 提交

- `58fdd4b feat(digest): wire JDBC write path with SQL rewrite and bind`
- 提交仅包含任务指定的四个 core 文件；本报告未纳入提交。

## 实现

- `SimpleInterceptorConnection` 在六个 `prepareStatement` 重载调用真实连接前，按 JDBC 模式和摘要配置改写 INSERT/UPDATE SQL，并把 `DigestRewriteResult` 传给包装后的 PreparedStatement。
- `SimpleInterceptorPreparedStatement` 单独保留调用方原始参数用于摘要计算；在 `executeQuery`、`executeUpdate`、`execute` 和 `addBatch` 前绑定摘要。
- UPDATE 新增 SET 占位符后，对应用侧参数索引做物理索引平移，避免 WHERE 参数误绑到摘要列。
- `DigestWriteSupport` 负责配置目标收集、单表校验、source 明文收集、UPDATE WHERE 参数提取、摘要计算和目标参数绑定。
- 摘要值直接绑定到底层 PreparedStatement，不经过 `ParameterEncryptor`，因此摘要列不会被加密；已有 target 参数会被重新计算值覆盖。
- 多行 INSERT 明确不改写、不注入；SELECT/DELETE 等非写 SQL 保持透传。

## 测试

- TDD RED：新增测试最初因 `DigestWriteSupport` 不存在而编译失败。
- 扩展 RED：多行 INSERT 测试最初观察到错误改写，随后增加跳过逻辑。
- 定向测试：`DigestWriteSupportTest` 4/4 通过。
- core 回归：29 tests，0 failures，0 errors，0 skipped。
- `git diff --check` 通过。

## 关注点

- `DigestSqlRewriter` 的多行 INSERT 参数索引仍存在已知限制，因此本任务在接入层显式跳过。
- Maven `clean` 在 Windows 上因 `securt-kit-core-1.0-SNAPSHOT.jar` 被占用而失败；不带 clean 的完整 core 测试已通过。
- 未修改 `SqlExecutor.java`；摘要索引在 execute 前同步到其现有 `parameterValues` 日志参数映射。

## Review Fixes

- 提交：`30f5127 fix(digest): correct multi-target bind and RELOAD where params`
- `DigestRewriteResult` 现在保留实际追加的 target 字段及其顺序；绑定追加参数时按 target/index 对应关系取摘要，已有 target 仅通过 `columnsByIndex` 路径覆盖。
- UPDATE RELOAD 的 WHERE 参数优先从 setter 加密后的存储值映射读取，缺失时回退到保留的明文映射；摘要 source 仍只使用保留明文。
- `DigestWriteSupport` 已注明 setter 阶段可能已完成字段加密，摘要始终根据单独保留的调用方明文计算。
- TDD RED：双摘要规则场景中，旧实现把已有 `phone_digest` 错绑到追加的 `id_card_digest` 参数。
- 验证：`mvn -pl securt-kit-core -am -Dmaven.test.skip=false -Dtest=DigestWriteSupportTest test`，6 tests，0 failures，0 errors，BUILD SUCCESS。
