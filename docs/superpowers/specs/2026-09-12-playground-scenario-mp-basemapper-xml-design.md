# Playground 复杂查询：BaseMapper + XML 场景执行设计

> 状态：已实现（OpenSpec apply 完成）  
> 版本：v1.0  
> 日期：2026-09-12  
> 页面：`/playground/` → Tab「复杂查询」  
> 目的：纠正「执行 SQL / 字面量改写」误解；场景「运行」必须走测试工程 MyBatis-Plus——简单条件用 BaseMapper/Wrapper，复杂查询用 XML，明文参数由 EncryptInterceptor 加密。  
> 关系：修正并细化 [2026-09-10-playground-mp-scenarios-design.md](./2026-09-10-playground-mp-scenarios-design.md)；与 [2026-09-12-playground-scenario-sql-workbench-design.md](./2026-09-12-playground-scenario-sql-workbench-design.md) 并存（SQL 工作台保留为旁路工具）。

---

## 1. 背景与问题

1. 用户在单表等值场景输入明文手机号 `13000000002`，库内为 `13000000002(加密)`，点「执行 SQL」得到 0 行。  
2. 根因：「执行 SQL」走原始 JDBC + 字面量 SQL，**不经过** MyBatis `EncryptInterceptor`，明文条件无法命中密文列。  
3. 正确演示路径应是业务代码写法：**简单 CRUD/条件 → MP BaseMapper/Wrapper；联查/别名/函数 → Mapper XML**。  
4. 已确认：保留 SQL 工作台；Boot2 与 Boot3 **一起**对齐。

---

## 2. 决策摘要

| 议题 | 决策 |
|------|------|
| 「运行场景」 | **必须**走 MP（插件加解密） |
| 简单场景 | `UserEntityMapper`（`BaseMapper`）+ `QueryWrapper` / `LambdaQueryWrapper` |
| 复杂场景 | 扩展现有 `UserOrderMapper`（或专用 `PlaygroundScenarioMapper`）+ **XML** |
| 密文栏 | 仍用 `SECURT_SKIP` JDBC 旁路 + 显式 `FieldCryptoService.encrypt` 条件（对照） |
| 「执行 SQL」 | **保留**；定位为旁路/对照工具；MYBATIS 下明文字面量不保证命中；UI 提示引导用「运行场景」或「密文示例 + SECURT_SKIP」 |
| 不改 | 人员台仍 JDBC 显式加解密；`securt-kit-playground` 仍无 MP 编译依赖 |
| 范围 | Boot2 + Boot3 同结构对齐 |

---

## 3. 目标与非目标

### 3.1 目标

1. `single-eq`：明文 `phone` 经 Wrapper → BaseMapper → EncryptInterceptor 加密后命中库内密文行；三栏有数据时 plain≠cipher。  
2. `like-phone`：同上（LikePatternHandler 语义写入 `sqlMeta`）。  
3. `join-user-orders` / `column-alias` / `table-alias` / `func-on-cipher`：明文路径用 **XML**（或 XML + 表别名）；`func-on-cipher` 保留 `sqlMeta.limitation`，允许 0 行。  
4. Boot2、Boot3 各有对称 Mapper/XML/Runner；至少各有一条场景 IT 冒烟。  
5. UI：保留「执行 SQL」；补充中文提示，避免再误用明文自由 SQL 期望插件加密。

### 3.2 非目标

- 不为「执行 SQL」在 playground 模块内做字面量自动加密改写。  
- 不把人员台迁到 MP。  
- 不新建演示业务表（仍 `user` / `orders`）。  
- 不保证函数作用在密文列上的明文语义。

---

## 4. 场景实现矩阵

| scenarioId | 明文路径（运行场景） | 密文栏 | sqlMeta 要点 |
|------------|----------------------|--------|--------------|
| `single-eq` | `UserEntityMapper.selectList(QueryWrapper.eq("phone", plain))` | skip + encrypt(phone) | Wrapper 说明 + mode |
| `like-phone` | `QueryWrapper.like("phone", pattern)` | skip + LIKE 旁路 | LikePatternHandler 类名与语义 |
| `table-alias` | XML：`FROM "user" u WHERE u.phone = #{phone}` | skip 等价 SQL | 表别名说明 |
| `column-alias` | XML：`phone AS mobile` + `WHERE phone = #{phone}` | skip | 别名映射说明 |
| `join-user-orders` | XML：user ⋈ orders（扩展现有或新增方法） | skip JOIN | 表名 user/orders |
| `func-on-cipher` | XML：`UPPER(phone)=UPPER(#{phone})` | skip 同条件 | **limitation**；允许 0 行 |

Mapper 归属建议：

- 简单：继续 `com.example.mapper.UserEntityMapper`（已有 BaseMapper）。  
- 复杂：在 `UserOrderMapper` + `UserOrderMapper.xml` **新增**场景用 select（或新建 `PlaygroundScenarioMapper` / `PlaygroundScenarioMapper.xml`，Boot2/3 各一份同结构）。  
  **推荐新建 `PlaygroundScenarioMapper`**：避免污染既有多表测试方法语义，场景 SQL 集中维护。

---

## 5. Runner 与装配

1. `MpPlaygroundScenarioRunner`（Boot2/Boot3）注入：`UserEntityMapper`、`PlaygroundScenarioMapper`（或 UserOrderMapper）、`DataSource`。  
2. `list()`：继续 catalog + sampleParams / exampleSqlPlain / exampleSqlCipher；明文示例 SQL **仅文档/编辑框用途**；cipher 示例用 `FieldCryptoService.encrypt` 动态生成后缀，避免写死 `(加密)`。  
3. `run()`：简单场景调 BaseMapper；复杂场景调 XML 方法；密文栏统一 skip 查询。  
4. Web 配置：有 Mapper bean 时组装 Runner（现有 ObjectProvider/可选注入模式保持）。

---

## 6. SQL 工作台（保留）

沿用既有 `seed-preview` / `sql-run`：

- `useSkip=true`：旁路读密文。  
- `useSkip=false`：**不**做字面量加密；`sqlMeta` 可增加 `note`：当前为原始 JDBC 执行，MYBATIS 模式下明文条件通常无法命中加密列，请使用「运行场景」。  
- UI：在「执行 SQL」旁增加一句固定中文提示。

---

## 7. 测试

1. Boot2/Boot3：插入或使用已知明文手机号（如 `13000000002` / 策略加密后入库），`single-eq`「运行场景」plain 非空且 phone 明文展示，cipher 列为密文形态。  
2. `join-user-orders`：XML 路径返回三栏结构。  
3. `func-on-cipher`：`sqlMeta.limitation` 存在。  
4. `sql-run` 明文无 skip：仍可成功执行但允许 0 行；有 skip 时 sql 含 token。  
5. 静态资源：存在执行 SQL 提示文案。

---

## 8. 文档

更新 `docs/PLAYGROUND.md`：

- 「运行场景」= MP BaseMapper / XML + 插件加密。  
- 「执行 SQL」= 旁路工具，MYBATIS 下勿期望字面量自动加密。

---

## 9. 风险

| 风险 | 缓解 |
|------|------|
| Wrapper 参数未进 EncryptInterceptor | 对照既有 `MybatisModeQueryWrapperTest`；确认 starter 注册 interceptor |
| Boot2/3 别名冲突 | IT 侧限定 `type-aliases-package` |
| 用户继续点「执行 SQL」 | UI 提示 + sqlMeta.note |

---

## 10. 实现分期

| 步 | 内容 |
|----|------|
| 1 | 新增 `PlaygroundScenarioMapper` + XML（Boot2）并改 Runner |
| 2 | Boot3 对称移植 |
| 3 | UI 提示 + PLAYGROUND.md |
| 4 | Boot2/Boot3 场景 IT 断言「运行场景」明文命中 |

请审阅本文件；确认后进入实现计划与编码。
