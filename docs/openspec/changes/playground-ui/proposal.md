## Why

四个测试工程目前主要靠 JUnit 与运维向 `/monitor` 验证行为；多数据源工程虽有 REST，但缺少统一的浏览器演示页。需要一套与 monitor 并存的 Playground，方便手工走通 CRUD、摘要、复杂查询与多数据源切换，同时保留 JUnit 做 CI 回归。

## What Changes

- 新增共享模块 `securt-kit-playground`（Engine / Dispatcher / 静态页，无 Servlet API）
- starter-boot2/3 增加薄 `PlaygroundStatViewServlet` 与自动装配，默认路径 `/playground`
- 四个测试工程启用 `securtkit.playground.*` 并更新 README
- 页面能力：CRUD、Digest、SQL（深链 monitor）、Complex 固定 action、Multi-DS 下拉
- **不**删除 JUnit；**不**把 playground 并入 monitor；**不**开放任意自由 SQL

## Capabilities

### New Capabilities
- `test-playground`: 测试工程交互演示页的配置、挂载、表白名单 API 与页面行为契约

### Modified Capabilities
- （无）`openspec/specs/` 下无既有 playground/monitor 行为契约需改；monitor 保持独立

## Impact

- **新模块**：`securt-kit-playground`；父 POM modules / profile 纳入
- **starter-boot2/3**：依赖 playground + Servlet 注册（对齐 monitor）
- **测试工程**：YAML 启用、可选 `PlaygroundDataSourceLocator`（多 DS）
- **文档**：INDEX / 各测试 README / 设计稿 `docs/superpowers/specs/2026-09-06-playground-ui-design.md`
