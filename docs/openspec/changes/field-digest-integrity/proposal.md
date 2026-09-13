## Why

字段加密后，库侧仍可能被直接改密文或摘要列；业务需要与加密策略协同的**完整性校验**（非检索盲索引）。本变更在现有 JDBC / MyBatis 加解密通道上增加配置驱动的行级摘要写入与可选读验签。

## What Changes

- 扩展 `securtkit.encryptor` 配置：全局 `digest-*` 默认项、表级 `digest` 列表、`digest-hmac-key`
- 扩展 `FieldEncryptorStrategy`：`supportsDigest` / `digest` / `verifyDigest`；内置 `HmacSha256DigestStrategy`
- 新增 `DigestService` / SQL 单表改写 / JDBC·MyBatis 写读挂接：写时按明文算摘要并落库；读时可选验签
- 部分 UPDATE 支持 `SKIP | RELOAD | FAIL`（默认 `RELOAD`）
- 启动校验：`target-field` 不与加密字段冲突、策略必须支持 digest 等
- 文档与 Boot2 集成测试补齐（USAGE、schema `row_digest`、验签失败策略用例）

非目标（本期不做）：JOIN 多表验签、MyBatis BATCH 完整语义、监控试算 UI、独立 `DigestStrategy` 接口、检索用 `search.type: HASH`

## Capabilities

### New Capabilities
- `field-digest-integrity`: 表级完整性摘要的配置、计算、单表 SQL 追加、双通道写读与可选验签行为

### Modified Capabilities
- （无）`openspec/specs/` 下尚无既有 capability；加密通道行为本身不改需求契约，仅新增摘要能力

## Impact

- **模块**：`securt-kit-core`（配置、策略、Digest*、JDBC 拦截）、`securt-kit-mybatis`（Parameter/Result 助手）、starter 配置绑定、`securt-kit-test-boot2`（IT）、`docs/USAGE.md`
- **API**：`FieldEncryptorStrategy` 新增 default 方法（向后兼容）；新增 `DigestMismatchException`
- **运维**：启用内置 HMAC 时必须配置 `digest-hmac-key`；业务表需摘要列（或依赖单表 SQL 改写追加）
- **现状（仓库观察）**：P0/P1 核心代码与单测已大部分落地（Tasks 1–8）；Boot2 IT / USAGE 收尾与 P2 边界打磨仍待完成
