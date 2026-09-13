# Playground 双模式与 MP 复杂查询演示设计

> 状态：已确认（待实现）  
> 版本：v1.0  
> 日期：2026-09-10  
> 页面：`/playground/`  
> 目的：人员维护台在 JDBC / MYBATIS 下均可演示；新增「复杂查询」页签，用 MyBatis-Plus 演示联查、别名、LIKE、函数等场景，并三栏展示明文 / 密文 / SQL。  
> 关系：扩展并修正 [2026-09-10-playground-person-crud-design.md](./2026-09-10-playground-person-crud-design.md) 中「仅 JDBC」假设；人员台技术栈仍为 JDBC（显式加解密），不改为 MP。

---

## 1. 背景与问题

1. 人员台当前依赖 JDBC 拦截器，`mode=MYBATIS` 时前置检查失败、落库不加密。  
2. 需要用 **MyBatis-Plus** 演示真实业务写法下的加密行为，且覆盖联查、别名、LIKE、函数等，而不仅是单表 CRUD。  
3. 人员维护台仍有独立价值，不宜整页替换为场景台。

---

## 2. 决策摘要

| 议题 | 决策 |
|------|------|
| 信息架构 | **双 Tab**：人员维护 + 复杂查询 |
| 人员台技术 | 继续 **JDBC**；改为 **显式加解密**（`FieldCryptoService` + digest），双模式可用 |
| 复杂查询技术 | 测试工程内 **MyBatis-Plus** Mapper / Wrapper |
| 数据表 | 复用现有 **`user` + `orders`**（不新建演示表） |
| 结果展示 | **三栏**：明文结果 \| 密文结果 \| SQL/说明 |
| playground 模块依赖 | **不**强制依赖 MP；场景执行器由 boot 测试工程注册 |

---

## 3. 目标与非目标

### 3.1 目标

1. 人员 CRUD 在 `mode=JDBC` 与 `mode=MYBATIS` 下均可：明文写入 → 库内密文 → 业务列表解密；原始视图为密文。  
2. 复杂查询页签提供固定场景卡片，至少包含：单表等值、user⋈orders 联查、列别名、表别名、加密列 LIKE、加密列函数（能力边界演示）。  
3. 每次场景执行返回 `plainRows`、`cipherRows`、`sqlMeta`（含 mode、SQL/Wrapper 说明、限制说明）。  
4. Boot2 / Boot3 测试工程可运行；文档说明双 Tab 与双模式。

### 3.2 非目标

- 人员台不迁到 MyBatis-Plus。  
- 不新建专用演示表。  
- 不恢复生命周期篡改向导。  
- 不保证「加密列上任意 SQL 函数」具备明文语义（函数场景用于展示限制）。  
- 不合并 Monitor。

---

## 4. 架构

```text
浏览器 /playground/
  ├─ Tab 人员维护  → PlaygroundPersonService (JDBC + FieldCryptoService)
  └─ Tab 复杂查询  → PlaygroundScenarioFacade
                         └─ PlaygroundScenarioRunner (SPI，boot 工程实现 / MP)
```

| 层 | 职责 |
|----|------|
| `securt-kit-playground` | 人员双模式改造；场景列表/运行 API 契约；UI 双 Tab + 三栏 |
| `securt-kit-test-boot2/3` | MP Mapper、场景实现、注册 Runner；保证 `user`/`orders` 种子与加密配置 |
| 多 DS 工程 | 按选中数据源执行同一套场景（与人员台一致） |

**模式行为**

- **人员写**：对配置字段 `encrypt`，计算 digest，使用 `SECURT_SKIP`（或等价）写入，避免 JDBC 模式下双重加密。  
- **人员业务读**：`SECURT_SKIP` 读出后 `decrypt`；原始读：仅 skip。  
- **复杂查询明文栏**：走 MP 正常查询（JDBC 模式依赖驱动拦截解密 / MYBATIS 模式依赖 MP 拦截解密）；若某 mode 下结果仍为密文，允许 fallback：raw + `FieldCryptoService.decrypt`。  
- **复杂查询密文栏**：同条件 `SECURT_SKIP` 查询或对结果列不做解密的旁路。

---

## 5. 人员台变更

相对 `2026-09-10-playground-person-crud-design.md`：

| 项 | 原约定 | 新约定 |
|----|--------|--------|
| Preflight `jdbc-mode` | 必须通过 | **降为信息项或删除硬门槛** |
| 写入 | 依赖 JDBC 拦截器 | **显式加密 + 摘要 + skip 写** |
| 业务列表 | 依赖拦截器解密 | **raw + decrypt** |
| 原始视图 | `SECURT_SKIP` | 不变 |

表 `playground_person`、字段与摘要规则不变。

---

## 6. 复杂查询场景

| scenarioId | 名称 | 实现要点 | 预期 |
|------------|------|----------|------|
| `single-eq` | 单表等值 | `QueryWrapper` / Lambda 按 `user.phone` | 明文条件命中；三栏对照 |
| `join-user-orders` | 用户订单联查 | `u`/`o` 表别名 JOIN | 加密列明文栏解密、密文栏密文 |
| `column-alias` | 列别名 | `phone AS mobile` 等 | 明文栏按配置映射解密；`sqlMeta` 说明别名 |
| `table-alias` | 表别名 | `FROM "user" u WHERE u.phone=?` | 别名解析下加解密正确 |
| `like-phone` | 加密列 LIKE | Wrapper `like` + `LikePatternHandler` | 默认精确匹配语义写入说明 |
| `func-on-cipher` | 加密列函数 | `UPPER`/`CONCAT` 等 | 返回 `sqlMeta.limitation`；允许 0 行成功 |

参数：场景定义带简单 schema（如 `phone`、`keyword`）；缺参 400。

---

## 7. API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/scenarios.json` | 场景列表 + 参数 schema + 是否可用 |
| POST | `/api/scenarios/run.json` | body: `scenarioId`, `params`, `datasourceId` → `plainRows`, `cipherRows`, `sqlMeta` |
| 现有 | `/api/person/*` | 行为改为双模式显式加解密 |

`PlaygroundScenarioRunner`（名称可调整）：

```text
List<ScenarioDescriptor> list();
ScenarioRunResult run(String scenarioId, Map params, String datasourceId);
```

Boot 工程提供实现并注入 Playground 引擎；未注册时复杂查询 Tab 显示不可用原因。

---

## 8. UI

1. 顶栏：标题、数据源、登录/退出、Monitor（不变）。  
2. 主 Tab：`人员维护` | `复杂查询`。  
3. 复杂查询：左场景列表；右参数 + 执行；下三栏——明文 / 密文 / SQL 与说明（窄屏纵向堆叠）。  
4. 人员台：保持现有维护台布局。

---

## 9. 错误与就绪

- 人员：检查表、白名单、加密字段、摘要、`SECURT_SKIP`；展示当前 mode 文本，**不因 MYBATIS 判失败**。  
- 复杂查询：无 Runner / 缺 `user`·`orders` / 未配置加密时场景 `available=false` 并中文说明。  
- 函数场景：业务上「未命中」仍可 `success=true`，限制写在 `sqlMeta.limitation`。

---

## 10. 测试

1. 人员：JDBC 与 MYBATIS 配置下各跑 create + 业务 list + 原始 list（明文 ≠ 密文）。  
2. 场景：boot2 至少 `single-eq`、`join-user-orders`、`like-phone` 返回三栏非空结构（数据具备时）。  
3. `column-alias` / `table-alias` / `func-on-cipher`：断言 `sqlMeta` 与响应结构。  
4. 静态资源：双 Tab、三栏容器存在。  
5. Boot3 对等冒烟（或同模块 profile）。

---

## 11. 文档

- 更新 `docs/PLAYGROUND.md`：双 Tab、双模式、场景列表。  
- 在人员 CRUD 设计文首增加「已被本设计扩展：人员改为显式加解密双模式」的状态注记（实现时修改）。

---

## 12. 分期建议

| 阶段 | 内容 |
|------|------|
| P0 | 人员显式加解密双模式 + UI 双 Tab 壳 + scenarios API 骨架 |
| P1 | MP Runner：`single-eq`、`join-user-orders`、`like-phone` + 三栏 |
| P2 | `column-alias`、`table-alias`、`func-on-cipher` + Boot3/多 DS |

本设计一次规格覆盖 P0–P2；实现可按阶段勾选任务。
