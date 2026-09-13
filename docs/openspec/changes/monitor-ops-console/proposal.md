## Why

现有 `/monitor` 只能看到部分加密配置，缺少摘要规则全览、单行验签、按条件刷数（预览后回写）以及 SELECT 密文/明文双视图。digest 能力已落地，运维需要在 monitor 上完成配置核对与数据修复，且与 `/playground` 演示页保持职责分离。

## What Changes

- 增强 `GET /api/config.json`：完整展示加密表/字段/策略、digest 规则与全局开关；密钥仅脱敏布尔
- 新增单值签名/验签 API，以及按表+id 的单行验签
- 新增刷数 `preview` → `apply`（按主键 UPDATE，单次 ≤500 行）；WHERE 受控，默认仅已配置表
- 增强 SQL 查询：仅 SELECT；同次返回 `cipherRows` + `plainRows`；强化参数加密 SQL 展示
- `MonitorProperties` 增加 `table-primary-keys` 等；主键解析：配置 → TableInfo → 临时输入（不落盘）
- 静态 UI 增加/增强 Tab：配置全览、验签、刷数、SQL 双视图
- **不**合并 playground；**不**开放任意 DML；**不**热写回 YAML

## Capabilities

### New Capabilities
- `monitor-ops-console`: Monitor 运维台配置全览、主键解析、单值/单行摘要工具、刷数预览回写、SELECT 双视图行为契约

### Modified Capabilities
- （无）`openspec/specs/` 下尚无既有 monitor 行为主规范；本 change 以新能力 delta 引入

## Impact

- **模块**：`securt-kit-monitor`（Engine / Dispatcher / DTO / 静态资源）为主
- **配置**：`securtkit.monitor.table-primary-keys` 等
- **可选依赖**：MyBatis-Plus 仅用于 TableInfo 解析（无则跳过）
- **文档**：产品设计 `docs/superpowers/specs/2026-09-06-monitor-ops-console-design.md`；USAGE / MONITOR-SERVLET / INDEX
- **测试**：monitor 单元 + boot2 集成冒烟；需 `skip-comment` 支撑密文视图
