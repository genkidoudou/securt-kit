## Why

当前 Monitor SQL 页虽已合并查询、解析和参数加密能力，但三个平级子模式未体现“只读查询”和“数据修改”的操作边界，较长的输入与结果还会持续撑高整页，迫使操作人员频繁纵向滚动。需要在不改变现有 API 的前提下重组工作区，使常用操作更清晰、结果浏览更紧凑。

## What Changes

- 将 SQL 页一级入口调整为「查询」和「修改」，默认进入查询
- 在修改区内提供「SQL 解析」和「参数加密」二级切换
- 查询区采用紧凑工具栏，并让查询结果在固定高度区域内滚动
- 修改区在桌面端采用输入与结果左右分栏，窄屏自动切换为上下布局
- 查询、SQL 解析和参数加密分别保留自己的 SQL 草稿与结果，模式切换不再相互覆盖
- 保留现有 DOM 标识、后端 API、请求字段及响应结构

## Capabilities

### New Capabilities

- `sql-workspace-ui`: Monitor SQL 工作区的查询/修改分区、模式状态隔离、固定高度结果浏览及响应式布局契约

### Modified Capabilities

- （无）主库 `openspec/specs/` 暂无已归档的 SQL 工作区规范；本 change 以新能力 delta 描述行为

## Impact

- **模块**：`securt-kit-monitor` 的 `support/http/resources/index.html`、`css/style.css` 和 `js/app.js`
- **接口**：不新增或修改后端 API
- **兼容**：保留现有 SQL 功能的 DOM ID 和事件入口，现有查询、解析、参数加密调用继续可用
- **文档与原型**：基于 `docs/superpowers/specs/2026-09-08-sql-page-redesign-design.md` 和 `docs/superpowers/mockups/sql-page-redesign/index.html`
- **验证**：三种 SQL 流程功能冒烟、草稿隔离、桌面/窄屏布局及结果区内部滚动
