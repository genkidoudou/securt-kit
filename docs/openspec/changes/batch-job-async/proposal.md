## Why

当前 Monitor 刷数把匹配行的 before/after 全量载入内存，并在一次 HTTP `apply` 内逐行 UPDATE，硬顶仅 500 行。联调库已到约 1 万行时，该模型会超时、撑爆内存且无法暂停/取消。需要把刷数升级为样本预览 + 异步分批作业，同时支持内存与目标库双存储。

## What Changes

- **BREAKING（刷数写库路径）**：废弃「全量预览快照 + 同步 `apply.json`」作为主路径；改为样本 preview → 创建异步 job → worker keyset 分批写库
- 新增作业 API：创建、状态轮询、暂停、继续、取消；终态含 SUCCEEDED / PARTIAL / FAILED / CANCELLED
- 作业存储可配置：`memory`（默认，重启丢失）与 `database`（目标库系统表，本阶段必须落地）
- Preview 仅返回样本 before/after + `totalEstimated`，业务行不再整表进作业快照
- Monitor UI 刷数页改为进度轮询与暂停/继续/取消；多表各自独立 job
- 可配置硬顶（默认 20000）、chunk/sample/失败明细上限
- 旧 `apply.json` 标记废弃（短期可兼容提示或拒绝并引导新 API）

## Capabilities

### New Capabilities
- `batch-job-async`: Monitor 异步刷数作业——样本预览、双存储、状态机（含暂停/继续/取消）、keyset 分批执行与部分成功契约

### Modified Capabilities
- （无正式主规范路径）既有 change `monitor-ops-console` 中「Batch preview then apply」500 行同步模型由本能力在行为上取代；主 `openspec/specs/` 尚无已归档的 monitor 规范可改，故以新能力 delta 引入

## Impact

- **模块**：`securt-kit-monitor`（`BatchJobStore`、Worker、`MonitorOpsFacade`、Dispatcher、静态 UI）
- **配置**：`securtkit.monitor.batch.*`（store / max-rows / chunk-size 等）
- **数据库（database 模式）**：目标库表 `securtkit_monitor_batch_job`、`securtkit_monitor_batch_failure`（H2/MySQL）
- **测试应用**：`securt-kit-test-boot2` 约 1 万行 seed 作为容量验证
- **文档**：产品设计 `docs/superpowers/specs/2026-09-10-batch-job-async-design.md`
- **非目标**：百万级、多实例租约、多表联合作业、data-init 整页重做
