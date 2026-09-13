# Securt-Kit 用户手册

Securt-Kit 是数据库**字段级透明加解密**框架：业务仍读写明文，框架在 **JDBC** 或 **MyBatis** 通道拦截参数与结果，对配置字段自动加密入库、解密返回。

| | |
|---|---|
| Spring Boot | 2.7.x（Java 8+） / 3.x（Java 17+） |
| 运维控制台 | [`/monitor/`](Monitor) |
| 演示验收页 | [`/playground/`](Playground)（测试工程，不随 starter 发布） |

## 文档导航

| 页面 | 说明 |
|------|------|
| [快速开始](Quick-Start) | 5 分钟接入依赖、通道与字段配置 |
| [使用指南](Usage) | 策略、Digest、LIKE、失败策略与最佳实践 |
| [Monitor 使用说明](Monitor) | 工作台、验签、刷数、SQL 双视图等运维能力 |
| [Playground](Playground) | 人员维护与复杂查询 SQL 工作台 |
| [多数据源](Multi-Datasource) | 按 `datasource-id` 差异化加密 |
| [常见问题](FAQ) | 排障与模式选择 |

## 仓库与 Wiki

- 源码仓库文档目录：[`doc/`](https://github.com/genkidoudou/securt-kit/tree/main/doc)（与本 Wiki 同步）
- 设计 / OpenSpec 等内部文档仍在仓库 [`docs/`](https://github.com/genkidoudou/securt-kit/tree/main/docs)，**不同步**到 Wiki
- 项目 README：[github.com/hexlodev/securt-kit](https://github.com/genkidoudou/securt-kit)

## 职责划分

| 工具 | 用途 |
|------|------|
| **Monitor** | 配置核对、明密文对照、验签、历史刷数 |
| **Playground** | 开发期 CRUD / 场景演示，不替代 JUnit 与 Monitor |
