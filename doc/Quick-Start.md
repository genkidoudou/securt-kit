# 快速开始

约 5 分钟完成依赖、通道与字段配置。更细的配置见 [使用指南](Usage)。

## 前置要求

- Java 8+（Boot 2.7）或 Java 17+（Boot 3.x）
- Spring Boot 2.7.x 或 3.x
- Maven 或 Gradle

## 1. 添加依赖

### Spring Boot 2.7.x

```xml
<dependency>
  <groupId>io.github.genkidoudou</groupId>
  <artifactId>securt-kit-starter-boot2</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

### Spring Boot 3.x

```xml
<dependency>
  <groupId>io.github.genkidoudou</groupId>
  <artifactId>securt-kit-starter-boot3</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

## 2. 选择通道

`securtkit.encryptor.mode`：`JDBC`（默认）| `MYBATIS` | `OFF`。  
**同一数据源禁止 JDBC 与 MYBATIS 同时生效。**

### JDBC（改驱动 + URL 前缀）

```yaml
spring:
  datasource:
    driver-class-name: io.github.genkidoudou.core.interceptor.SimpleInterceptorDriver
    url: jdbc:interceptor:mysql://localhost:3306/testdb
    username: root
    password: password

securtkit:
  encryptor:
    enable: true
    mode: JDBC
```

### MYBATIS（保持原驱动）

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/testdb

securtkit:
  encryptor:
    enable: true
    mode: MYBATIS
```

Starter 会注册 MyBatis 加密拦截插件（classpath 需有 MyBatis）。

## 3. 配置加密字段

```yaml
securtkit:
  encryptor:
    enable: true
    failure-policy: FALLBACK
    tables:
      - table-name: user
        fields:
          - field-name: name
          - field-name: phone
```

## 4. 可选：打开 Monitor

```yaml
securtkit:
  monitor:
    enabled: true
    path: /monitor
    username: admin
    password: admin
```

浏览器打开 `http://localhost:8080/monitor/`，详见 [Monitor 使用说明](Monitor)。

## 下一步

- [使用指南](Usage) — 自定义策略、Digest、LIKE、失败策略  
- [多数据源](Multi-Datasource)  
- [常见问题](FAQ)
