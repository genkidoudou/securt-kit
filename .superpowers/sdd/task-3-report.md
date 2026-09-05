# Task 3 Report: DigestConfigRegistry + 启动校验

## 状态

已完成 Task 3。新增不可变摘要规则、线程安全配置注册表，并在
`ConfigInitializer` 启动流程中完成摘要配置校验、默认值解析、HMAC 策略注册和规则注册；
初始化失败及 `reset()` 均会清空摘要注册表。

## TDD RED

1. 先创建 `DigestConfigRegistryTest`，覆盖：
   - 摘要目标字段不能同时是加密字段（忽略大小写）
   - 同表摘要目标字段不能重复（忽略大小写）
   - sourceFields 非空、策略必须支持 digest
   - HMAC 密钥必填并注册带密钥的策略实例
   - 规则注册、全局默认值解析、项目级覆盖与 reset 清理
2. 首次按简报原命令执行时，PowerShell 将未加引号的
   `-Dmaven.test.skip=false` 错误解析为 Maven 生命周期阶段；改为引号包裹参数后重跑。
3. RED 命令：
   `mvn -pl securt-kit-core -am "-Dmaven.test.skip=false" "-Dtest=DigestConfigRegistryTest" test`
4. RED 结果：失败于测试编译，缺少 `io.github.hexlodev.core.digest.ResolvedDigestRule`，
   证明测试在生产实现出现前失败。

## TDD GREEN

实现内容：

- `ResolvedDigestRule`
  - final 类和 final 字段
  - sourceFields 防御性复制并暴露不可变列表
  - 保存 tableName、sourceFields、targetField、strategyClass、partialUpdate、
    verifyOnRead、failurePolicy
- `DigestConfigRegistry`
  - `datasourceId -> lower-case tableName -> immutable rules`
  - 提供 `register`、`getRules`、`hasDigest`、`clear`
  - 空 datasourceId 归一化为 `default`
- `ConfigInitializer`
  - 校验 sourceFields、targetField、同表 targetField 唯一性及与加密字段互斥
  - 项目级 strategy 覆盖全局 digestStrategy，策略必须实现
    `FieldEncryptorStrategy` 且 `supportsDigest() == true`
  - HMAC 策略要求非空 digestHmacKey，并通过 `StrategyCache.registerStrategy`
    注册 `new HmacSha256DigestStrategy(digestHmacKey)`
  - 按项目级、全局、最终默认顺序解析 partialUpdate、verifyOnRead、failurePolicy
  - 验证全部成功后批量写入 registry；初始化失败和 reset 时清理 registry

GREEN 定向命令：
`mvn -pl securt-kit-core -am "-Dmaven.test.skip=false" "-Dtest=DigestConfigRegistryTest" test`

结果：6 tests，0 failures，0 errors，BUILD SUCCESS。

## 完整验证

命令：
`mvn -pl securt-kit-core -am "-Dmaven.test.skip=false" test`

结果：10 tests，0 failures，0 errors，BUILD SUCCESS。

## 注意事项

- Maven 输出存在项目已有的 SLF4J NOP logger 提示，不影响测试结果。
- 全工作区 `git diff --check` 报告 `docs/QUICK-START.md:47` 有尾随空格；
  该文件属于任务外既有脏改动，未修改、未暂存。
- `ConfigInitializer.java` 在任务开始前已有 LIKE handler、统一加解密门面和 mode
  相关 WIP；工作区内容保持不变，提交时仅暂存 Task 3 摘要相关增量。

## Important Review Findings 修复

- 修复同一 `datasourceId + tableName` 的多个 `TableConfig` 之间摘要目标字段与加密字段互斥校验。
- 新增 `sourceFields` 元素级校验，拒绝 `null`、空字符串及纯空白字段名。
- RED 命令：
  `mvn -pl securt-kit-core -am "-Dmaven.test.skip=false" "-Dtest=DigestConfigRegistryTest" test`
- RED 结果：8 tests，2 failures；两项新增回归测试均因未抛出异常而失败。
- GREEN 命令：
  `mvn -pl securt-kit-core -am "-Dmaven.test.skip=false" "-Dtest=DigestConfigRegistryTest" test`
- GREEN 结果：8 tests，0 failures，0 errors，BUILD SUCCESS。
