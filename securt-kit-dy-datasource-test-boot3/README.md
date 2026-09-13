# Securt-Kit 多数据源测试项目（Spring Boot 3.x）

## 简介

本测试项目演示了 Securt-Kit 在 Spring Boot 3.x 多数据源场景下的使用方法。

## 与 Boot 2.7 版本的差异

- **Java 版本**：需要 Java 17+
- **依赖**：使用 `securt-kit-starter-boot3`
- **包名**：使用 `jakarta.*` 替代 `javax.*`
- **其他**：功能和使用方式完全相同

## 快速开始

```bash
cd securt-kit-dy-datasource-test-boot3
mvn spring-boot:run
```

启动后访问 Playground：`http://localhost:8081/playground/`。

默认页面是 `playground_person` 人员维护台：业务/原始双视图 + CRUD。切换 primary / secondary / third 会重新检查所选数据源并刷新列表。

配置方式与 Boot 2.7 版本完全相同，参考 [securt-kit-dy-datasource-test-boot2/README.md](../securt-kit-dy-datasource-test-boot2/README.md)。

## 相关文档

- [主 README](../../README.md)
- [使用指南](../../docs/USAGE.md)
