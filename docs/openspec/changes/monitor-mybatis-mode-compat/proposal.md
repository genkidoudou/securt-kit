## Why

`mode=MYBATIS` 时加解密只挂在 MyBatis 插件上，Monitor 的 SQL 查询与单行验签走纯 JDBC，明文/密文对照失效，验签常因读到密文源字段而误判。运维需要在 MYBATIS 模式下也能用这两项工具核对库内数据。

## What Changes

- Monitor SQL 查询在任意加密模式下提供一致的双视图：密文为库内原值，明文为按配置解密后的结果。
- Monitor 单行验签在计算/比对摘要前，对已配置加密的摘要源字段先解密再验签。
- 实现为 Monitor 侧结果后处理（策略 / `FieldCryptoService`），**不**重新打开 JDBC 拦截通道，避免与 MYBATIS 双重加密。
- 更新 Monitor 帮助/文档中关于 MYBATIS 与 SQL/验签关系的说明。
- 非目标：不改业务 MyBatis 通道；不改刷数作业主路径；不做复杂 SQL 表达式列的智能解密。

## Capabilities

### New Capabilities

- `monitor-mybatis-mode-compat`: Monitor 在 MYBATIS（及 JDBC）模式下对 SQL 查询双视图与单行验签的模式无关结果解密行为。

### Modified Capabilities

- 无。`openspec/specs/` 尚无已归档的 Monitor 主规格；本变更以独立增量能力描述。

## Impact

- `securt-kit-monitor`：`MonitorEngine.querySql`、`MonitorOpsFacade.rowVerify`（及可抽取的结果解密辅助）、相关单元测试。
- 文档：Monitor 帮助文案 / `docs` 中 MYBATIS 与运维工具说明。
- 不要求业务应用改配置；`mode=JDBC` 行为应对齐为同一套后处理或保持外观一致。
