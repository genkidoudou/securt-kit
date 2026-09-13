## Why

复杂查询场景能跑固定 scenario，但页面上看不到 `user`/`orders` 演示数据，也缺少可编辑的只读 SQL 执行入口，对照写 SQL 成本高。需要在 `/playground` 复杂查询 Tab 内常驻双表预览，并支持示例 SQL 展示与 SELECT（含可选 SECURT_SKIP）执行。

## What Changes

- 复杂查询 Tab 顶部常驻两个只读列表：`user`、`orders`（可刷新，优先 SECURT_SKIP 展示库内原值）
- 场景目录扩展：`sampleParams`、`exampleSqlPlain`、`exampleSqlCipher`、`sampleHint`
- 新增 `GET /api/scenarios/seed-preview.json`、`POST /api/scenarios/sql-run.json`（仅单语句 SELECT；可选自动加 skip）
- UI：SQL 编辑框、明文/密文示例切换、SECURT_SKIP 勾选、执行 SQL；保留运行场景与三栏证据
- 更新 `docs/PLAYGROUND.md`

## Capabilities

### New Capabilities
- `playground-scenario-sql-workbench`: Playground 复杂查询双表种子预览、场景示例 SQL 元数据、只读 SQL 执行与安全边界

### Modified Capabilities
- （无；主规格库尚未归档同名能力，以新能力 delta 引入）

## Impact

- **代码**：`securt-kit-playground`（Engine/Dispatcher/DTO/静态 UI）、`MpPlaygroundScenarioRunner`（boot2/boot3 示例 SQL 与 sampleParams）
- **API**：新增 seed-preview / sql-run；扩展 scenarios 列表字段（向后兼容）
- **安全**：禁多语句与写操作；行数上限；skip 依赖现有 skip-comment 配置
- **非破坏**：现有 `scenarios/run.json` 行为保持
