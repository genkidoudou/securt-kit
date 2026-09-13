## Why

人员维护台依赖 JDBC 拦截器，`mode=MYBATIS` 时不可用；同时需要用 MyBatis-Plus 演示联查、别名、LIKE、函数等真实查询场景，且结果要能对照明文、密文与 SQL。现有单表人员台仍有价值，不宜整页替换。

## What Changes

- **人员台双模式**：写入改为显式加密+摘要（`FieldCryptoService` / digest），业务读 raw+decrypt；preflight **不再**将 JDBC mode 作为硬失败条件（**BREAKING** 相对「必须 JDBC」的就绪语义）
- **UI 双 Tab**：保留人员维护；新增「复杂查询」页签
- **复杂查询 API**：`GET /api/scenarios.json`、`POST /api/scenarios/run.json`，返回 `plainRows` / `cipherRows` / `sqlMeta`
- **MP 场景执行器**：由 boot 测试工程实现并注册（playground 模块不强制依赖 MP）；复用 `user` + `orders`
- 固定场景：`single-eq`、`join-user-orders`、`column-alias`、`table-alias`、`like-phone`、`func-on-cipher`
- 更新 `docs/PLAYGROUND.md`

## Capabilities

### New Capabilities
- `playground-mp-scenarios`: Playground 人员台双模式显式加解密，以及 MP 复杂查询场景列表/执行与三栏结果契约

### Modified Capabilities
- （无正式主规范路径）既有 change `playground-person-crud` 中「要求 JDBC 模式」的就绪语义由本能力覆盖；主 `openspec/specs/` 尚无已归档 person 规范可改，故以新能力引入

## Impact

- **模块**：`securt-kit-playground`（PersonService、Engine、Dispatcher、静态 UI）；`securt-kit-test-boot2/3`（MP Runner、Mapper）
- **可选**：多 DS 测试工程按数据源挂同一 Runner
- **依赖**：测试工程已有 MP；playground 仅 SPI/接口
- **文档**：产品设计 `docs/superpowers/specs/2026-09-10-playground-mp-scenarios-design.md`
- **非目标**：人员台迁 MP、新建演示表、生命周期篡改向导、保证加密列函数明文语义
