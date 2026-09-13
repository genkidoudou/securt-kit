# 常见问题

## 如何选择 Boot2 / Boot3？

- Spring Boot 2.7.x → `securt-kit-starter-boot2`  
- Spring Boot 3.x → `securt-kit-starter-boot3`  

## JDBC 与 MyBatis 可以同时开吗？

**同一数据源不可以。** 否则参数可能被重复处理。设置唯一的 `securtkit.encryptor.mode`。

## 数据库里还是明文？

1. 「配置信息」确认 `enable`、`mode`、表字段  
2. JDBC：驱动与 `jdbc:interceptor:` 前缀  
3. MYBATIS：插件是否加载、Mapper 是否走 MyBatis  
4. 写入路径是否带 `SECURT_SKIP`（旁路则不会自动加密）

## SQL 解析没有占位符映射？

只有 `?` 会产生映射。字面量 SQL 无映射正常；已配置的加密字段仍应被识别。

## 支持哪些数据库？

基于 JDBC 的库（MySQL、PostgreSQL、Oracle、H2 等）。复杂 SQL 解析能力随方言与语句形态变化，可用 [Monitor](Monitor) SQL 工具验证。

## 加密列 LIKE 查不到？

默认 ExactMatch：含 `%`/`_` 时常无法按明文模糊语义命中。无通配符时按精确匹配加密后再比。见 [使用指南](Usage)。

## Monitor SQL 在 MYBATIS 下明文栏为空/不对？

Monitor 查询双视图与验签在 MYBATIS 下会对配置字段**显式解密**，不依赖 JDBC 拦截。若仍异常：核对表字段配置、skip 读是否拿到原值、函数是否改写了密文形态。

## Playground 场景全部不可用？

宿主未注册 `PlaygroundScenarioRunner`，或 `user.phone` 等未配置加密。种子预览与 sql-run 仍可依赖数据源使用。见 [Playground](Playground)。

## 性能影响？

开启 SQL 解析缓存、仅配置必要字段。具体以压测为准。

## 更完整的内部设计文档？

仓库 [`docs/`](https://github.com/genkidoudou/securt-kit/tree/main/docs)（设计稿 / OpenSpec），**不同步**本 Wiki。用户手册以仓库 [`doc/`](https://github.com/genkidoudou/securt-kit/tree/main/doc) 为准。
