# Playground

`/playground/` 是测试工程共用的**浏览器手工验收页**，**不随 starter 发布**，也不替代 JUnit。运维请用 [Monitor](Monitor)。

## 启用（测试工程）

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

入口示例：

- 单数据源：`http://localhost:8080/playground/`
- 多数据源测试工程：`http://localhost:8081/playground/`

## 双 Tab

| Tab | 说明 |
|-----|------|
| **人员维护** | 表 `playground_person` CRUD；显式加解密；JDBC / MYBATIS 均可 |
| **复杂查询** | 固定场景 + 双表种子预览 + 只读 SQL 工作台 |

## 人员维护

就绪检查要点：表白名单、列结构、`phone`/`id_card` 加密、摘要 `phone + id_card → row_digest`、`SECURT_SKIP` 开启。加密模式仅作信息展示，**不再因 MYBATIS 判未就绪**。

能力：业务视图提交明文并写摘要（经 skip 落库防双重加密）；业务视图解密展示；原始视图看库内密文；条件查询对敏感字段服务端加密后再比。

## 复杂查询

需宿主注册 `PlaygroundScenarioRunner`（Boot2/Boot3 的 `MpPlaygroundScenarioRunner`）。未注册时场景列表可见但 `available=false`；种子预览与 sql-run 仍可用。

### 固定场景（示例）

| scenarioId | 说明 |
|------------|------|
| `single-eq` | `user.phone` 等值 |
| `like-phone` | 加密列 LIKE（注意通配符与 ExactMatch） |
| `join-user-orders` | user ⋈ orders |
| `column-alias` / `table-alias` | 别名 |
| `func-on-cipher` | 加密列函数边界（允许 0 行） |

「运行场景」走 MyBatis 加密路径，密文栏用 `SECURT_SKIP` 对照。

### 双表预览与 SQL 工作台

1. 顶部常驻 `user` / `orders` 预览（优先 skip 读库内原值，可刷新）  
2. 选场景填充 `sampleParams` 与明文示例 SQL；可切密文示例；勾选 SECURT_SKIP 后执行**单条 SELECT**  
3. 三栏证据：场景运行写满明文/密文/SQL；自由 SQL 按是否 skip 映射  

自由 SQL **不会**把明文字面量自动改成密文；MYBATIS 下明文自由 SQL 常无法命中加密列，加密列匹配请用「运行场景」或「密文示例 + SECURT_SKIP」。

### 相关 API

- `GET /api/scenarios.json`  
- `POST /api/scenarios/run.json`  
- `GET /api/scenarios/seed-preview.json`  
- `POST /api/scenarios/sql-run.json`（仅 SELECT；禁 `;` 与写操作）
