# Playground 复杂查询：种子数据双表预览与 SQL 工作台设计

> 状态：已确认（待实现）  
> 版本：v1.0  
> 日期：2026-09-12  
> 页面：`/playground/` → Tab「复杂查询」  
> 目的：常驻展示演示表 `user` / `orders` 数据列表，并支持场景示例 SQL 展示与可编辑 SELECT 执行（含可选 `SECURT_SKIP`）。  
> 关系：扩展 [2026-09-10-playground-mp-scenarios-design.md](./2026-09-10-playground-mp-scenarios-design.md)。

---

## 1. 背景与问题

复杂查询场景已能跑固定 scenario，但操作者仍需离开页面查种子数据与示例 SQL，不便对照写 SQL。需要在 Playground 内直接看到两张演示表数据，并能执行（只读）SQL。

---

## 2. 决策摘要

| 议题 | 决策 |
|------|------|
| 数据展示 | **两个常驻列表**：`user` 与 `orders`（种子/库内当前行预览） |
| SQL 执行 | 场景示例 + 可编辑 SELECT；可选自动加 `SECURT_SKIP`（方案 C） |
| 场景样例 | 选中场景时填充推荐参数 + 明文/密文示例 SQL |
| 实现路径 | 扩展场景目录元数据 + `seed-preview` / `sql-run` API + UI |
| 安全 | 仅单语句 SELECT；禁多语句与写操作；行数上限 |

---

## 3. 目标与非目标

### 3.1 目标

1. 复杂查询 Tab **始终可见**两张表的数据列表（`user`、`orders`），方便对照写 SQL。  
2. 支持刷新预览（`SECURT_SKIP` 读库，避免列表被解密成与库内不一致而误导）。  
3. 选场景时展示推荐参数、示例 SQL（明文路径 / 密文旁路），可一键载入编辑框。  
4. 「运行场景」保持现有三栏；「执行 SQL」对编辑框内容做只读查询，结果进入证据区。  
5. 勾选「使用 SECURT_SKIP」时自动为 SQL 添加 skip 注释（若尚未包含）。

### 3.2 非目标

- 不执行 INSERT/UPDATE/DELETE/DDL/多语句。  
- 不替代 Monitor SQL 工作区。  
- 不新建演示表。  
- 不在双表列表上做行内编辑。

---

## 4. API

### 4.1 扩展 `GET /api/scenarios.json`

每个场景描述可增加（缺省兼容旧客户端）：

| 字段 | 含义 |
|------|------|
| `sampleParams` | 推荐参数 map |
| `exampleSqlPlain` | 明文路径示例 SQL |
| `exampleSqlCipher` | 密文旁路示例 SQL（可含 skip 注释或说明将由 useSkip 添加） |
| `sampleHint` | 相关种子说明一句中文 |

### 4.2 `GET /api/scenarios/seed-preview.json`

- Query：`datasourceId`（可选）  
- 响应：
  - `userRows`：最多 N 行（默认 50，硬顶 100）  
  - `ordersRows`：同上  
  - `encryptNote`：如「phone / customer_phone 等配置字段库内为密文；本预览经 SECURT_SKIP 读取」  
  - `limits`：实际 limit  

读取使用 `SECURT_SKIP`（若配置启用），保证列表展示**库内原值**，便于写密文条件。

### 4.3 `POST /api/scenarios/sql-run.json`

Body：

```json
{
  "sql": "SELECT ...",
  "useSkip": true,
  "datasourceId": null
}
```

行为：

1. Trim；拒绝空 SQL。  
2. 拒绝含 `;` 的多语句（字符串字面量内除外可采用保守策略：直接禁 `;`）。  
3. 去掉前导注释后必须以 `SELECT` 开头（忽略大小写）。  
4. 拒绝明显写操作关键字作语句头（INSERT/UPDATE/DELETE/MERGE/DDL…）。  
5. `useSkip=true` 且未含 skip token 时，前置 `/* SECURT_SKIP */`。  
6. 执行查询，行数截断到上限；返回 `rows`、`sqlMeta`（`sql` 实际执行文本、`encryptMode`、`skipped`、`truncated`）。

### 4.4 现有 `POST /api/scenarios/run.json`

不变。跑完后 UI 可将 `sqlMeta` 中的 SQL/说明同步到编辑框，便于改写后再 `sql-run`。

---

## 5. UI 布局（复杂查询 Tab）

自上而下、第一视口优先保证「双表可见」：

```text
┌─ 演示数据 ─────────────────────────────────────┐
│ [刷新预览]  encryptNote                         │
│ ┌─ user 列表 ──────┐  ┌─ orders 列表 ─────────┐ │
│ │ 表格 / 空态       │  │ 表格 / 空态            │ │
│ └──────────────────┘  └───────────────────────┘ │
└─────────────────────────────────────────────────┘
┌─ 场景 ──────────┬─ 运行 ────────────────────────┐
│ 场景列表         │ 参数 / 运行场景                 │
│                  │ SQL 编辑框                       │
│                  │ [明文示例][密文示例]             │
│                  │ ☑ SECURT_SKIP  [执行 SQL]       │
└──────────────────┴────────────────────────────────┘
┌─ 三栏：明文 | 密文 | SQL/说明 ───────────────────┐
└──────────────────────────────────────────────────┘
```

交互要点：

1. 进入复杂查询 Tab 或切换数据源时自动拉 `seed-preview`。  
2. 选中场景：填 `sampleParams`；默认载入 `exampleSqlPlain`；可点「密文示例」切换。  
3. 「执行 SQL」：`useSkip` 勾选状态传入 API；结果：  
   - 未 skip：`rows` → 明文栏；密文栏可留空或提示未请求旁路。  
   - 已 skip：`rows` → 密文栏，并可复制一份到明文栏旁注「当前为 skip 原值」或明文栏显示同一结果并在 SQL 栏说明。  
   **约定**：skip 时三栏中「密文」展示 `rows`，「SQL」展示 `sqlMeta`；「明文」显示提示「本次为 SECURT_SKIP 原值，未做结果解密」+ 同 rows（避免空白）。  
4. 「运行场景」仍写满三栏（plain/cipher/sqlMeta）。

窄屏：双表纵向堆叠，再场景，再三栏。

---

## 6. 场景示例内容（boot 工程提供）

由 `MpPlaygroundScenarioRunner` / catalog 填充，与现有种子一致，例如：

| scenarioId | sampleParams | 示例 SQL 要点 |
|------------|--------------|---------------|
| single-eq | phone=`13800138000` | `WHERE phone = ?` / skip + 密文等值 |
| join-user-orders | userId=`1` | JOIN skip SQL |
| column-alias | phone=… | `phone AS mobile` |
| table-alias | phone=… | `FROM "user" u` |
| like-phone | pattern=`13800138000` | `LIKE` |
| func-on-cipher | phone=… | `UPPER(phone)=UPPER(?)` + limitation |

---

## 7. 测试

1. seed-preview：两表数组存在；含 skip 时 phone 列呈现密文形态（如带 `(加密)`）。  
2. sql-run：合法 SELECT 成功；含 `;` / `UPDATE` 返回 400。  
3. useSkip：实际 SQL 含 skip token（配置启用时）。  
4. UI 静态：存在 user/orders 两个列表容器、SQL 编辑框、执行按钮。  
5. 场景目录含 `sampleParams` / example SQL 字段（有 Runner 时）。

---

## 8. 文档

更新 `docs/PLAYGROUND.md`：复杂查询双表预览与 SQL 执行说明。

---

## 9. 风险

| 风险 | 缓解 |
|------|------|
| 大表预览拖慢页面 | 默认 limit 50，硬顶 100 |
| 用户误以为可写库 | UI 文案标明只读 SELECT |
| skip 未开启 | sql-run 在 useSkip 且配置关闭时返回明确错误 |
