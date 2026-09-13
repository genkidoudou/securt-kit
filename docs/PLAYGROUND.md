# Playground

`/playground/` 是四个测试工程共用的浏览器手工验收页，不随 starter 发布，也不替代 JUnit。

## 双 Tab

| Tab | 说明 |
|-----|------|
| **人员维护** | 固定表 `playground_person` 的 CRUD；显式加解密，JDBC / MYBATIS 双模式可用 |
| **复杂查询** | MyBatis-Plus 固定场景；三栏展示明文 / 密文 / SQL 说明（需宿主注册 `PlaygroundScenarioRunner`） |

## 人员维护

页面启动时检查：

- 当前数据源可用；
- `playground_person` 位于 `securtkit.playground.allowed-tables`；
- 表包含 `id`、`name`、`phone`、`id_card`、`age`、`row_digest`；
- `phone` 与 `id_card` 已配置加密；
- 摘要规则为 `phone + id_card -> row_digest`；
- `securtkit.encryptor.skip-comment.enable=true` 且 token 为 `SECURT_SKIP`；
- 当前加密模式仅作信息展示，**不再因 MYBATIS 判定未就绪**。

能力：

1. **新增 / 编辑 / 删除**：业务视图提交明文；服务端 `FieldCryptoService` 加密并写摘要，经 `/* SECURT_SKIP */` 落库（避免 JDBC 双重加密）。
2. **业务视图**：skip 读出后显式解密。
3. **原始视图**：skip 读出，展示库内密文。
4. **条件查询**：姓名 / 手机号 / 身份证精确匹配（敏感字段明文入参，服务端加密后比对）。

## 复杂查询场景

| scenarioId | 说明 | 明文执行路径 |
|------------|------|--------------|
| `single-eq` | `user.phone` 等值 | `UserEntityMapper` + `QueryWrapper` |
| `like-phone` | 加密列 LIKE（无通配符时 ExactMatch 加密后精确匹配） | `PlaygroundScenarioMapper` XML `LIKE #{pattern}`（勿用 MP `QueryWrapper.like`，其会自动加 `%` 导致默认 Handler 跳过加密） |
| `join-user-orders` | `user` ⋈ `orders` | `PlaygroundScenarioMapper` XML |
| `column-alias` | `phone AS mobile` | `PlaygroundScenarioMapper` XML |
| `table-alias` | `FROM "user" u` | `PlaygroundScenarioMapper` XML |
| `func-on-cipher` | 加密列函数边界（`sqlMeta.limitation`，允许 0 行） | `PlaygroundScenarioMapper` XML |

**「运行场景」**走 MyBatis-Plus（EncryptInterceptor 加密绑定参数），密文栏仍用 `SECURT_SKIP` + 显式加密对照。

复杂查询 Tab 还提供：

1. **双表种子预览**：顶部常驻 `user` / `orders` 列表（默认经 `SECURT_SKIP` 读库内原值，可刷新）。
2. **SQL 工作台（旁路）**：选场景时填充 `sampleParams` 与明文示例 SQL；可切换密文示例；勾选「使用 SECURT_SKIP」后执行只读单条 `SELECT`。**不会**改写明文字面量为密文；MYBATIS 下明文自由 SQL 通常无法命中加密列（见 UI 提示与 `sqlMeta.note`）。加密列匹配请用「运行场景」或「密文示例 + SECURT_SKIP」。
3. **三栏证据**：运行固定场景仍写满明文 / 密文 / SQL；执行 SQL 时按是否 skip 映射到对应栏。

API：

- `GET /playground/api/scenarios.json`（可含 `sampleParams` / `exampleSqlPlain` / `exampleSqlCipher` / `sampleHint`；cipher 示例由 `FieldCryptoService.encrypt` 动态生成）
- `POST /playground/api/scenarios/run.json` → `{ plainRows, cipherRows, sqlMeta }`
- `GET /playground/api/scenarios/seed-preview.json` → `{ userRows, ordersRows, encryptNote, limits }`
- `POST /playground/api/scenarios/sql-run.json` → `{ rows, sqlMeta }`（仅单语句 SELECT；禁 `;` 与写操作；MYBATIS 非 skip 时 `sqlMeta.note` 提示旁路语义）

宿主通过 Spring 注入 `PlaygroundScenarioRunner`（Boot2/Boot3 的 `MpPlaygroundScenarioRunner`，依赖 `UserEntityMapper` + `PlaygroundScenarioMapper`）。未注册时场景列表仍可打开，但全部 `available=false`；种子预览与 sql-run 仍可用（依赖数据源与表白名单）。

## 配置要点

```yaml
securtkit:
  encryptor:
    mode: JDBC   # 或 MYBATIS
    skip-comment:
      enable: true
      token: SECURT_SKIP
  playground:
    enabled: true
    path: /playground
    auth-enabled: false
    allowed-tables: [user, digest_user, orders, playground_person]
```

Boot2 profile：`application-mybatis.yml` / `application-jdbc.yml` 切换 mode 与驱动。

## 多数据源

多数据源测试工程可为各 DS 创建 `playground_person` 并应用一致规则；切换数据源会刷新人员就绪检查。复杂查询默认使用宿主主数据源 Mapper。

## 入口

- 单数据源 Boot2/Boot3：`http://localhost:8080/playground/`
- 多数据源 Boot2/Boot3：`http://localhost:8081/playground/`
