## Why

现有 Playground 主页是围绕 `digest_user` 的分步生命周期向导，不利于日常验证「维护一张表 + 加密落库 + 列表解密 + 查看库内密文」。测试人员更需要经典 CRUD 维护台，并能在同一列表上切换业务视图与原始视图。

## What Changes

- **BREAKING（UI）**：移除生命周期向导作为默认主页；改为人员表维护台（筛选、列表、新增/编辑弹窗、删除）。
- 新建演示表 `playground_person`（姓名、手机号、身份证、年龄、摘要）；手机号与身份证加密落库，摘要源为二者。
- 新增 `person` CRUD API：`list`（`view=business|raw`）、`create`、`update`、`delete`。
- 业务列表走拦截器解密；原始列表旁路读取展示库内密文。
- 支持按姓名、手机号、身份证条件查询（敏感字段精确匹配）。
- 旧 `/api/lifecycle/*` 不再挂 UI；可删除或标注废弃。
- 测试工程建表与加密/摘要/白名单配置对齐新表。

## Capabilities

### New Capabilities

- `playground-person-crud`: Playground 人员表维护台的数据模型、CRUD API、业务/原始双视图与条件查询行为。

### Modified Capabilities

- 无。`openspec/specs/` 尚无已归档的 Playground 主规格；进行中的 `playground-lifecycle-redesign` 描述向导方案，本变更以新能力取代其作为实现方向（不修改该变更目录内文件）。

## Impact

- `securt-kit-playground`：服务、Dispatcher、DTO、静态 HTML/CSS/JS、单元测试。
- Boot2/Boot3 及多数据源测试工程：建表、`application.yml` 字段/摘要/白名单、相关 IT 与文档。
- API：新增 `/playground/api/person/*`；生命周期 API 降级或移除。
- 配置：加密字段 `phone`/`id_card`，摘要 `source-fields: [phone, id_card]` → `row_digest`。
