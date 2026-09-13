# Securt-Kit 多数据源测试项目（Spring Boot 2.7）

## 简介

本测试项目演示了 Securt-Kit 在多数据源场景下的使用方法，支持为不同数据源配置不同的加密规则。

## 项目结构

```
securt-kit-dy-datasource-test-boot2/
├── src/main/java/io/github/test/
│   ├── MultiDataSourceApplication.java  # 启动类
│   ├── entity/                          # 实体类
│   ├── mapper/                          # MyBatis Mapper
│   ├── service/                         # 服务类
│   │   ├── MultiDataSourceUserService.java
│   │   ├── ComplexQueryService.java
│   │   ├── BatchOperationService.java
│   │   ├── TransactionService.java
│   │   └── AdvancedSqlService.java
│   ├── controller/                      # 控制器
│   │   ├── TestController.java
│   │   └── ComplexTestController.java
│   ├── config/                          # 配置类
│   │   └── DatabaseInitializer.java
│   └── MyFieldEncryptorStrategy.java    # 自定义加密策略
└── src/main/resources/
    ├── application.yml                   # 配置文件
    └── mapper/                          # MyBatis XML
```

## 快速开始

### 1. 运行测试项目

```bash
cd securt-kit-dy-datasource-test-boot2
mvn spring-boot:run
```

### 2. 访问测试接口

- 基础测试：`http://localhost:8081/test/primary`
- 复杂场景测试：`http://localhost:8081/api/complex/*`

### 3. 访问监控界面

`http://localhost:8081/monitor/index.html`

### 3.1 访问 Playground 演示页

`http://localhost:8081/playground/`

默认页面是 `playground_person` 人员维护台：业务/原始双视图 + CRUD。顶栏可切换 primary / secondary / third；切换后会重新执行前置检查并刷新列表。各数据源均初始化独立的 `playground_person` 表和一致的 `phone+id_card -> row_digest` 规则。

配置项：`securtkit.playground.enabled=true`。  
Playground 仅测试工程依赖，不随 starter 发布。

## 多数据源配置

### 数据源配置

```yaml
spring:
  datasource:
    dynamic:
      primary: primary
      datasource:
        primary:
          driver-class-name: io.github.genkidoudou.core.interceptor.SimpleInterceptorDriver
          url: jdbc:interceptor:h2:mem:primary_db;datasource-id=primary
        secondary:
          driver-class-name: io.github.genkidoudou.core.interceptor.SimpleInterceptorDriver
          url: jdbc:interceptor:h2:mem:secondary_db;datasource-id=secondary
        third:
          driver-class-name: io.github.genkidoudou.core.interceptor.SimpleInterceptorDriver
          url: jdbc:interceptor:h2:mem:third_db;datasource-id=third
```

### 加密配置

```yaml
securtkit:
  encryptor:
    enable: true
    tables:
      # 主数据源配置
      - table-name: user
        datasource-id: primary
        fields:
          - field-name: name
          - field-name: phone
      # 从数据源配置
      - table-name: user
        datasource-id: secondary
        fields:
          - field-name: email
      # 第三方数据源不加密
```

## 测试场景

### 1. 基础 CRUD 操作

- 单数据源操作
- 多数据源切换
- 字段加密解密验证

### 2. 复杂查询

- JOIN 查询
- 子查询
- 聚合查询

### 3. 批量操作

- 批量插入
- 批量更新
- 批量查询

### 4. 事务处理

- 单数据源事务
- 跨数据源事务（注意：需要分布式事务支持）

## API 接口

### 基础测试接口

- `GET /test/primary` - 测试主数据源
- `GET /test/secondary` - 测试从数据源
- `GET /test/third` - 测试第三方数据源

### 复杂场景接口

- `POST /api/complex/batch` - 批量操作
- `POST /api/complex/transaction` - 事务测试
- `POST /api/complex/query` - 复杂查询

## 关键点

1. **数据源 URL 必须包含 `datasource-id` 参数**
2. **表配置中必须指定 `datasource-id`**
3. **不同数据源可以有不同的加密字段配置**

## 相关文档

- [主 README](../../README.md)
- [使用指南](../../docs/USAGE.md)
- [多数据源配置说明](../../docs/MULTI-DATASOURCE.md)
