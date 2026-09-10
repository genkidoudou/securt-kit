# Playground 人员表 CRUD 重设计

> 状态：待用户审阅  
> 日期：2026-09-10  
> 页面：`/playground/`  
> 目的：用单表维护台演示敏感字段加密落库、列表解密，以及切换查看数据库原始密文。  
> 关系：取代 `2026-09-08-playground-ui-redesign-design.md` 中的生命周期向导方案（该文档作废，不再实现）。

---

## 1. 背景与问题

现有 Playground 已做成「加密数据生命周期」分步向导，绑定 `digest_user`，侧重单条记录的叙事演示。实际使用上更需要：

- 维护一张清晰的演示表（含手机号、身份证等敏感字段）；
- 常规增删改查；
- 一眼对比「业务解密视图」与「库内密文视图」。

本设计将主页改为经典表维护台，去掉分步向导与篡改叙事。

## 2. 目标与非目标

### 2.1 目标

1. 新建并只维护表 `playground_person`。
2. 支持新增、修改、删除、条件查询。
3. 新增/修改时 `phone`、`id_card` 加密落库；`row_digest` 由框架按源字段自动维护。
4. 业务列表展示解密后的手机号、身份证。
5. 同一列表可切换「原始视图」，展示库内密文。
6. 条件查询支持按姓名、手机号、身份证号筛选（敏感字段精确匹配）。

### 2.2 非目标

- 不做生命周期向导、篡改演示、证据对照栏。
- 不替代 JUnit / 集成测试。
- 不把 Playground 合并到 Monitor。
- 本版不分页；不做身份证号强格式校验。
- 不新增加密/摘要算法。

## 3. 数据模型与配置

### 3.1 表 `playground_person`

| 列 | 类型意图 | 说明 |
|---|---|---|
| `id` | BIGINT PK 自增 | 主键 |
| `name` | VARCHAR | 明文 |
| `phone` | VARCHAR | 加密落库；摘要源之一 |
| `id_card` | VARCHAR | 加密落库；摘要源之一 |
| `age` | INT | 明文 |
| `row_digest` | VARCHAR | 框架自动维护，只读展示 |

可与现有 `digest_user` 并存；本页只操作新表。测试工程启动时建表。

### 3.2 框架配置

- 加密字段：`phone`、`id_card`
- 摘要规则：`source-fields: [phone, id_card]` → 写入 `row_digest`
- `securtkit.playground.allowed-tables`（及对应数据源表白名单）包含 `playground_person`

### 3.3 读写约定

| 场景 | 行为 |
|---|---|
| 新增 / 修改 | 业务 SQL 走拦截器：敏感字段加密，摘要重算 |
| 业务列表 / 条件查询 | 走拦截器：敏感字段解密为明文 |
| 原始视图列表 | 旁路读取（如 `/* SECURT_SKIP */`）：敏感字段为库内密文，摘要原样 |
| 删除 | 按 `id` 删除 |

## 4. 页面与交互

### 4.1 布局

1. **顶栏**：标题「人员维护」、数据源选择、登录/退出、Monitor 链接  
2. **工具条**：视图切换 `业务视图 | 原始视图`；「新增」  
3. **筛选区**：姓名、手机号、身份证号 +「查询 / 重置」  
4. **主表**：id、姓名、手机号、身份证号、年龄、摘要、操作（编辑 / 删除）  
5. **表单**：新增/编辑共用弹窗（姓名、手机、身份证、年龄）；摘要不出现在可编辑表单中  

### 4.2 交互规则

- 默认业务视图：完成全部 CRUD；敏感列显示明文。
- 切换原始视图：沿用当前筛选条件，请求 `view=raw`；敏感列显示密文。
- 原始视图下禁用编辑/删除（前端拦截）；提示回到业务视图操作。
- 查询：
  - `name`：明文列匹配（实现可选等值或简单模糊，规格要求至少等值可用）。
  - `phone` / `id_card`：精确匹配；请求传明文，由框架加密后与库比较。
- 删除需二次确认；成功后刷新当前视图列表。

## 5. API 与数据流

主路径改为 `person` 资源。旧 `/api/lifecycle/*` 不再挂 UI；可删除或暂留但文档标明废弃。

| API | 作用 | 拦截器 |
|---|---|---|
| `POST /api/person/list.json` | 条件列表；`view=business\|raw` | business 正常；raw 旁路 |
| `POST /api/person/create.json` | 新增 | 正常：加密 + 摘要 |
| `POST /api/person/update.json` | 按 id 修改 | 正常：加密 + 重算摘要 |
| `POST /api/person/delete.json` | 按 id 删除 | 正常 |
| 现有 datasources / login / logout | 不变 | — |

### 5.1 请求体

- 公共：`datasourceId`
- list：`name?`、`phone?`、`idCard?`、`view`（`business` \| `raw`）
- create：`name`、`phone`、`idCard`、`age`
- update：`id` + create 字段
- delete：`id`

### 5.2 数据流

```
表单明文 → create/update（拦截器）→ DB 密文 + row_digest
list(view=business) → 拦截器解密 → 表格明文
list(view=raw)      → SECURT_SKIP   → 表格密文
```

### 5.3 实现边界

- 新服务：`PlaygroundPersonService`（或同等命名），固定表名 `playground_person`。
- 前端重写为维护台；移除向导步骤状态与证据快照 UI。
- 写路径只接受业务明文，不提供「按密文回写」API。

## 6. 错误处理

- 缺必填、非法年龄等：业务失败 + 中文消息，表单旁提示。
- id 不存在：失败提示，建议刷新列表。
- 数据源不可用、表未建、未进白名单、加密/摘要未配置：页面顶部告警条（针对 `playground_person` 的就绪检查）。
- 原始视图误调写接口：后端仍只走明文业务写语义；前端应先拦截。
- 统一 `ApiResponse`；失败不得把半截数据标为成功。

## 7. 测试

1. create 后：raw 中 phone/id_card 非明文；business 为明文；row_digest 非空。  
2. update 变更 phone 或 id_card 后：raw 密文变化；摘要变化；business 为新明文。  
3. 按 phone / idCard 精确查询可命中；错误明文不命中。  
4. delete 后 business/raw 均查不到。  
5. 缺表或缺配置时 list/create 返回可读错误。  
6. 前端冒烟：视图切换列形态不同；CRUD 后列表刷新。

## 8. 成功标准

测试人员打开 Playground 后可以：新增一条含手机号与身份证的记录 → 业务列表看到明文 → 切到原始视图看到密文 → 按手机号/身份证查出该行 → 修改后再对比两视图 → 删除。

---

## 修订记录

| 日期 | 说明 |
|---|---|
| 2026-09-10 | 初稿：人员表 CRUD + 业务/原始视图切换 |
