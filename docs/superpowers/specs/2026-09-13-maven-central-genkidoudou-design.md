# Maven Central 发布与命名空间迁移设计

> 状态：已确认执行  
> 日期：2026-09-13  
> 命名空间：`io.github.genkidoudou`

## 决策

1. Maven `groupId` 与 Java 包名均由 `io.github.hexlodev` 改为 `io.github.genkidoudou`。
2. 使用 Central Publisher Portal + `central-publishing-maven-plugin`。
3. 仅发布库模块；测试工程与 playground 跳过 deploy。
4. SCM：`https://github.com/genkidoudou/securt-kit`。

## 发布模块

- securt-kit-core
- securt-kit-mybatis
- securt-kit-monitor
- securt-kit-starter-boot2
- securt-kit-starter-boot3

## 不发布

- securt-kit-test-boot2 / boot3
- securt-kit-playground
- securt-kit-dy-datasource-test-boot2 / boot3

## 发布配置

- `release` profile：GPG 签名、source/javadoc、central-publishing-maven-plugin 0.10.0
- `settings.xml` server id：`central`（Portal User Token）
