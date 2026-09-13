## Why

现有 `/playground` 将 CRUD、Digest、SQL、Complex 和 Multi-DS 分散为多个工具标签，并直接输出 JSON，测试人员无法直观看清一条业务数据从明文写入、加密落库与摘要生成，到查询解密和完整性校验的完整链路。当前 raw query 也只标记 `rawHint`，并未证明读取的是绕过拦截后的数据库原始值，需要在重设计中建立可验证的三视角契约。

## What Changes

- 将默认页面重构为围绕单条 `digest_user` 记录的六步生命周期工作台：新增、查询并解密、修改敏感字段、再次查询、校验摘要、篡改并验证。
- 每一步统一展示请求明文、数据库原始密文与摘要、正常业务查询解密结果，并由后端返回结构化断言和步骤状态。
- 新增生命周期 preflight 与操作 API；流程状态保存在浏览器端，重置不会删除数据库记录。
- 使用现有 `SECURT_SKIP` 机制进行真实原始读取和受限篡改，并限制表、字段和篡改目标。
- 将 SQL、Complex 和 Multi-DS 移入“更多测试”次级区域，保留现有能力但不再占据主流程。
- 保留现有 Playground 挂载、鉴权、数据源选择和 JUnit 回归能力，不引入新算法或任意 SQL。

## Capabilities

### New Capabilities

- `playground-lifecycle`: 定义 Playground 单记录加密生命周期、三视角快照、摘要校验与篡改验证的页面和 API 行为。

### Modified Capabilities

- 无。现有 `openspec/specs/` 尚无已归档的 Playground 主规格；已完成但未归档的 `playground-ui` 变更继续描述基础挂载和旧能力，本变更以独立增量能力描述生命周期重设计。

## Impact

- `securt-kit-playground`：`PlaygroundEngine`、`PlaygroundDispatcher`、生命周期 DTO、静态 HTML/CSS/JavaScript 和测试。
- Boot2/Boot3 单数据源及多数据源测试工程：用于生命周期 preflight、加密摘要集成验证及必要文档说明。
- API：新增 `/playground/api/lifecycle/*`；既有 CRUD、Digest、Complex API 暂时保留以支撑“更多测试”和兼容现有用法。
- 安全：原始读取和篡改固定使用 `SECURT_SKIP`，仅允许 `digest_user` 及服务端定义的字段和动作。
