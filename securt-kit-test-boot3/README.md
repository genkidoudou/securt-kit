# Securt-Kit 测试项目（Spring Boot 3.x）

## 简介

本测试项目演示了 Securt-Kit 在 Spring Boot 3.x 环境下的使用方法，包含完整的示例代码和配置。

## 项目结构

```
securt-kit-test-boot3/
├── src/main/java/com/example/
│   ├── TestApplication.java          # 启动类
│   ├── entity/                       # 实体类
│   ├── mapper/                       # MyBatis Mapper
│   ├── dto/                          # DTO 类
│   ├── config/                       # 配置类
│   ├── MyFieldEncryptorStrategy.java # 自定义加密策略
│   └── test/                         # 测试运行器
└── src/main/resources/
    ├── application.yml                # 配置文件
    ├── schema.sql                    # 数据库表结构
    ├── data.sql                      # 初始数据
    └── mapper/                       # MyBatis XML
```

## 快速开始

### 1. 运行测试项目

```bash
cd securt-kit-test-boot3
mvn spring-boot:run
```

**注意**：需要 Java 17+ 环境。

### 2. 访问监控界面

启动后访问：`http://localhost:8080/monitor/index.html`

### 2.1 访问 Playground 演示页

启动后访问：`http://localhost:8080/playground/`

默认页面是 `playground_person` 人员维护台：筛选、列表、新增/编辑/删除，并可在「业务视图（解密）」与「原始视图（库内密文）」间切换。手机号与身份证加密落库，摘要源为二者。

页面前置检查要求 JDBC 模式、`phone`/`id_card` 加密、`phone+id_card -> row_digest` 摘要规则以及 `SECURT_SKIP` 开启。

**注意：** Playground 仅存在于本测试工程，**不会**随 `securt-kit-starter-*` 发布。配置项：`securtkit.playground.enabled=true`。

## 与 Boot 2.7 版本的差异

- **Java 版本**：需要 Java 17+
- **依赖**：使用 `securt-kit-starter-boot3`
- **包名**：使用 `jakarta.*` 替代 `javax.*`
- **其他**：功能和使用方式完全相同

## 配置说明

配置方式与 Boot 2.7 版本完全相同，参考 [securt-kit-test-boot2/README.md](../securt-kit-test-boot2/README.md)。

## 相关文档

- [主 README](../../README.md)
- [使用指南](../../docs/USAGE.md)
- [快速开始](../../docs/QUICK-START.md)
