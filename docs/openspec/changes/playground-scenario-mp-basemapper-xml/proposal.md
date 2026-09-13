## Why

Playground「复杂查询」在 MYBATIS 下用「执行 SQL」跑明文字面量条件会 0 行，因为该路径走原始 JDBC、不经 `EncryptInterceptor`。演示应对齐真实业务写法：简单条件用 MyBatis-Plus BaseMapper/Wrapper，复杂查询用 XML，明文参数由插件加密后再比对密文列。

## What Changes

- 重构 Boot2/Boot3 的 `MpPlaygroundScenarioRunner`：「运行场景」明文路径按场景分流——简单场景走 `UserEntityMapper`（BaseMapper + Wrapper），复杂场景走新建 `PlaygroundScenarioMapper` + XML
- 密文栏仍用 `SECURT_SKIP` JDBC 旁路 + 显式加密条件对照；cipher 示例 SQL 用 `FieldCryptoService.encrypt` 动态生成
- 保留 SQL 工作台（seed-preview / sql-run）；非 skip 时不改写字面量；UI 与 `sqlMeta.note` 提示 MYBATIS 下明文自由 SQL 不保证命中
- 更新 `docs/PLAYGROUND.md` 与静态提示文案；Boot2/Boot3 对称 IT（`single-eq` 明文命中等）

## Capabilities

### New Capabilities

- `playground-scenario-mp-basemapper-xml`: 复杂查询「运行场景」必须经 MP BaseMapper/XML 加密路径；SQL 工作台保留为旁路工具并带明确提示

### Modified Capabilities

- （无主库已归档 capability 需改写；行为增量以本 change 下 delta 为准）

## Impact

- 代码：`securt-kit-test-boot2` / `securt-kit-test-boot3` 的 playground Runner、新建 Scenario Mapper/XML、Playground Web 装配注入；`securt-kit-playground` UI/文案与可选 `sqlMeta.note`（无 MP 依赖）
- API：场景 list/run 契约不变；sql-run 增加说明性 note，不改变只读 SELECT 安全规则
- 依赖：测试工程已有 mybatis-plus；playground 模块仍不引入 MP
- 文档：`docs/PLAYGROUND.md`；产品设计见 `docs/superpowers/specs/2026-09-12-playground-scenario-mp-basemapper-xml-design.md`
