## Why

现有 `/monitor` 虽已具备运维 API（配置全览、刷数、双视图等），但前端仍是 8 个平铺 Tab、视觉陈旧、SQL 三能力割裂，缺少运维向工作台首页。运维排查场景需要更快定位与更清晰的交互，且不破坏已落地的后端契约。

## What Changes

- 重做 monitor 静态 UI（`index.html` / `css/style.css` / `js/app.js`）：深色顶栏 + 浅色内容、统一结果面板
- 新增默认 **工作台** Tab：环境摘要 + 快捷入口（SQL 对照 / 刷数 / 配置）
- 将「SQL 解析 / SQL 参数加密 / SQL 查询」合并为单一 **SQL** Tab，内含子模式；默认「查询双视图」
- 刷数 Tab 增加步骤指示；未成功 preview 时禁用 apply
- 登录成功（或免登录）后默认进入工作台，而非「加密解密」
- **默认不新增后端 API**；工作台摘要由前端组合现有 `config.json` + `datasources.json`（仅当组合成本过高时才可选 `overview.json`）
- **不**引入侧栏或场景向导壳；**不**合并 playground；**不**改加密核心

## Capabilities

### New Capabilities
- `monitor-ui`: Monitor 浏览器 UI 的信息架构、默认首页、SQL 子模式合并、刷数步骤态与视觉/交互契约（相对现有平铺 Tab UI）

### Modified Capabilities
- （无）主库 `openspec/specs/` 下尚无已归档的 monitor UI 主规范；`monitor-ops-console` 侧重 API/运维能力，本 change 以新 UI 能力 delta 引入，不修改其 API 需求条文

## Impact

- **模块**：`securt-kit-monitor` 静态资源为主；Java/API 默认不动
- **兼容**：现有 `/api/*` 契约保持；静态资源路径仍为 `/monitor/css|js/...`
- **文档**：产品设计 `docs/superpowers/specs/2026-09-07-monitor-ui-redesign-design.md`；线框 `docs/superpowers/mockups/monitor-redesign/`；实现后更新 `MONITOR-SERVLET.md`
- **测试**：现有 `MonitorOpsUnitTest` 不回退；UI 以手动冒烟为主（test-boot2 `/monitor/`）
