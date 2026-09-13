# Securt-Kit - 数据库字段加密解密框架

[![Java Version](https://img.shields.io/badge/Java-8%2B-blue.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7%20%7C%203.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## 📋 目录

- [项目简介](#项目简介)
- [核心特性](#核心特性)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [模块说明](#模块说明)
- [配置说明](#配置说明)
- [使用示例](#使用示例)
- [监控界面](#监控界面)
- [常见问题](#常见问题)
- [贡献指南](#贡献指南)
- [文档](#文档)

---

## 📖 项目简介

**Securt-Kit** 是一个基于 JDBC 拦截技术的数据库字段加密解密框架，提供透明的数据库字段加密解密功能。该框架可以在不修改业务代码的情况下，对指定的数据库表和字段进行自动加密和解密。

### 设计理念

- **透明加密**：业务代码无需修改，框架自动处理加密解密
- **JDBC 拦截**：基于 JDBC 驱动代理技术，在数据库操作层面拦截和处理
- **灵活配置**：支持多种配置方式，按表和字段粒度配置
- **高性能**：优化的 SQL 解析和缓存机制，最小化性能影响
- **多版本支持**：同时支持 Spring Boot 2.7.x 和 3.x

### 适用场景

- ✅ 敏感数据保护（身份证号、手机号、银行卡号等）
- ✅ 合规要求（GDPR、个人信息保护法等）
- ✅ 数据安全审计
- ✅ 数据库加密字段管理

---

## ✨ 核心特性

### 1. 透明加密解密
- 无需修改业务代码
- 自动识别需要加密的字段
- 自动处理 INSERT、UPDATE、SELECT 操作

### 2. 灵活的配置方式
- 支持 YAML/Properties 配置文件
- 支持多数据源配置
- 支持自定义加密策略

### 3. 高性能
- SQL 解析结果缓存
- 优化的字符串操作
- 最小化性能影响

### 4. 完善的监控功能
- Web 监控界面
- 加密解密测试
- SQL 解析测试
- 配置信息查看

### 5. 多版本支持
- Spring Boot 2.7.x（Java 8+）
- Spring Boot 3.x（Java 17+）

---

## 🚀 快速开始

### 1. 添加依赖

**Spring Boot 2.7.x 项目**：
```xml
<dependency>
    <groupId>io.github.genkidoudou</groupId>
    <artifactId>securt-kit-starter-boot2</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**Spring Boot 3.x 项目**：
```xml
<dependency>
    <groupId>io.github.genkidoudou</groupId>
    <artifactId>securt-kit-starter-boot3</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置数据源

修改数据源驱动为拦截驱动：

```yaml
spring:
  datasource:
    driver-class-name: io.github.genkidoudou.core.interceptor.SimpleInterceptorDriver
    url: jdbc:interceptor:mysql://localhost:3306/testdb
    username: root
    password: password
```

### 3. 配置加密字段

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
      - table-name: order
        fields:
          - field-name: customer_name
```

### 4. 实现加密策略（可选）

```java
@Component
public class MyFieldEncryptorStrategy implements FieldEncryptorStrategy {
    @Override
    public String encrypt(String plainText) {
        // 实现加密逻辑
        return AESUtil.encrypt(plainText);
    }
    
    @Override
    public String decrypt(String cipherText) {
        // 实现解密逻辑
        return AESUtil.decrypt(cipherText);
    }
}
```

### 5. 使用

框架会自动处理加密解密，业务代码无需修改：

```java
@Autowired
private UserMapper userMapper;

// 插入时自动加密
public void createUser(User user) {
    userMapper.insert(user);  // name 和 phone 字段会自动加密
}

// 查询时自动解密
public User getUserById(Long id) {
    return userMapper.selectById(id);  // name 和 phone 字段会自动解密
}
```

---

## 📁 项目结构

```
securt-kit/
├── securt-kit-core/                    # 核心模块（版本无关）
├── securt-kit-monitor/                # 监控 UI 共享模块（Druid 风格，无 Servlet 依赖）
├── securt-kit-mybatis/                # MyBatis 拦截通道（mode=MYBATIS）
├── securt-kit-starter-boot2/          # Spring Boot 2.7 启动器（薄 Servlet 适配）
├── securt-kit-starter-boot3/          # Spring Boot 3.x 启动器（薄 Servlet 适配）
├── securt-kit-test-boot2/             # Boot 2.7 测试项目
├── securt-kit-test-boot3/             # Boot 3.x 测试项目
├── securt-kit-dy-datasource-test-boot2/  # Boot 2.7 多数据源测试
└── securt-kit-dy-datasource-test-boot3/  # Boot 3.x 多数据源测试
```

---

## 🔧 模块说明

### securt-kit-core

**核心模块**，提供所有基础功能，版本无关（Java 8）。

**主要组件**：
- **拦截器层**：JDBC 驱动拦截、连接包装、语句包装
- **解析器层**：SQL 解析、字段识别、表名提取
- **配置层**：配置属性管理、表配置缓存
- **策略层**：加密策略接口

### securt-kit-starter-boot2 / securt-kit-starter-boot3

**Spring Boot 启动器**，提供自动配置和监控 UI。

**功能**：
- 自动配置 `FieldEncryptorProperties`
- 自动初始化 `TableCache`
- 内置监控 UI（加密解密测试、SQL 解析、配置查看）

监控 UI 采用 Druid 同款思路：业务与静态资源在 `securt-kit-monitor`，各 Starter 仅注册对应 Servlet 适配层。详见 [监控 UI Servlet 改造](docs/MONITOR-SERVLET.md)。

**监控 UI 访问地址**：`http://localhost:8080/monitor/index.html`

### securt-kit-test-boot2 / securt-kit-test-boot3

**测试项目**，提供完整的使用示例。

### securt-kit-dy-datasource-test-boot2 / securt-kit-dy-datasource-test-boot3

**多数据源测试项目**，演示多数据源场景下的加密配置。

---

## ⚙️ 配置说明

### 基础配置

```yaml
securtkit:
  encryptor:
    enable: true                    # 是否启用加密功能
    failure-policy: FALLBACK        # 失败处理策略：FALLBACK/FAIL_FAST/RETRY/SKIP
    sql-parse-cache:
      enable: true                  # 是否启用 SQL 解析缓存
      max-size: 1000                # 缓存最大容量
    tables:
      - table-name: user            # 表名
        datasource-id: primary      # 数据源标识（多数据源场景，可选）
        fields:
          - field-name: name        # 字段名
            strategy: com.example.MyStrategy  # 自定义策略（可选）
```

### 多数据源配置

```yaml
securtkit:
  encryptor:
    tables:
      - table-name: user
        datasource-id: primary      # 主数据源
        fields:
          - field-name: name
      - table-name: user
        datasource-id: secondary    # 从数据源
        fields:
          - field-name: email
```

### 监控配置

```yaml
securtkit:
  monitor:
    enabled: true                   # 是否启用监控界面
    username: admin                 # 登录用户名
    password: admin                 # 登录密码
    path: /monitor                  # 访问路径
```

---

## 💡 使用示例

### 单数据源场景

```yaml
securtkit:
  encryptor:
    enable: true
    tables:
      - table-name: user
        fields:
          - field-name: name
          - field-name: phone
```

### 多数据源场景

```yaml
securtkit:
  encryptor:
    enable: true
    tables:
      - table-name: user
        datasource-id: primary
        fields:
          - field-name: name
      - table-name: order
        datasource-id: secondary
        fields:
          - field-name: customer_name
```

### 自定义加密策略

```java
@Component
public class CustomEncryptorStrategy implements FieldEncryptorStrategy {
    @Override
    public String encrypt(String plainText) {
        // 自定义加密逻辑
        return Base64.getEncoder().encodeToString(plainText.getBytes());
    }
    
    @Override
    public String decrypt(String cipherText) {
        // 自定义解密逻辑
        return new String(Base64.getDecoder().decode(cipherText));
    }
}
```

配置中使用：
```yaml
securtkit:
  encryptor:
    tables:
      - table-name: user
        fields:
          - field-name: name
            strategy: com.example.CustomEncryptorStrategy
```

---

## 🖥️ 监控界面（Monitor）

启动应用并启用 Monitor 后，访问 `http://localhost:8080/monitor/`（路径可配）。

完整说明见：**[doc/Monitor.md](doc/Monitor.md)** · Wiki：[Monitor](https://github.com/genkidoudou/securt-kit/wiki/Monitor)

### 功能概览

| 能力 | 说明 |
|------|------|
| **工作台** | 默认首页：模式 / 表数量 / 数据源摘要与快捷入口 |
| **加密解密** | 单值加密、解密、签名、验签 |
| **验签** | 按表 + 主键读行、算摘要、比对（JDBC / MYBATIS 均可） |
| **刷数作业** | 历史数据批量加解密 / 摘要回填（**先预览再回写**） |
| **SQL** | 查询明密文双视图；SQL 解析；参数加密（只生成不执行） |
| **配置信息** | 运行时生效的表字段、策略与 Digest |
| **项目文档** | 页内离线精简接入指南 + 各页「使用说明」弹窗 |

### 安全说明

- ⚠️ 生产请启用认证并更换默认口令
- ⚠️ 建议内网或反向代理访问
- ⚠️ SQL 查询仅支持 SELECT；刷数会改库，务必备份

### 配置示例

```yaml
securtkit:
  monitor:
    enabled: true
    username: admin
    password: admin
    path: /monitor
```

---

## ❓ 常见问题

### Q1: 如何选择 Boot2 还是 Boot3 版本？

**A**: 根据你的 Spring Boot 版本选择：
- Spring Boot 2.7.x → 使用 `securt-kit-starter-boot2`
- Spring Boot 3.x → 使用 `securt-kit-starter-boot3`

### Q2: 支持哪些数据库？

**A**: 支持所有基于 JDBC 的数据库（MySQL、PostgreSQL、Oracle、H2 等）。

### Q3: 性能影响如何？

**A**: 框架经过优化，性能影响最小：
- SQL 解析结果缓存
- 优化的字符串操作
- 仅在需要加密的字段上处理

### Q4: 如何实现自定义加密策略？

**A**: 实现 `FieldEncryptorStrategy` 接口，并在配置中指定策略类名。

### Q5: 多数据源如何配置？

**A**: 在表配置中指定 `datasource-id`，并在数据源 URL 中添加 `datasource-id` 参数。

---

## 🤝 贡献指南

欢迎贡献代码！请参考 [CONTRIBUTING.md](CONTRIBUTING.md)。

---

## 📄 许可证

本项目采用 **Apache License 2.0** 许可证。

---

## 📚 文档

### 用户手册（`doc/`，同步 GitHub Wiki）

- [手册首页](doc/Home.md)
- [快速开始](doc/Quick-Start.md)
- [使用指南](doc/Usage.md)
- [Monitor 使用说明](doc/Monitor.md)
- [Playground](doc/Playground.md)
- [多数据源](doc/Multi-Datasource.md)
- [常见问题](doc/FAQ.md)
- [Wiki 同步说明](doc/README.md)

在线 Wiki：https://github.com/genkidoudou/securt-kit/wiki

### 仓库内其它文档

- [文档索引](docs/INDEX.md) - 设计稿 / OpenSpec / 模块说明索引
- [快速开始（docs）](docs/QUICK-START.md)
- [使用指南（docs）](docs/USAGE.md)
- [多数据源配置（docs）](docs/MULTI-DATASOURCE.md)
- [Playground 手册（docs）](docs/PLAYGROUND.md)
- [MyBatis / JDBC 双模式融合设计](docs/MYBATIS-MODE-DESIGN.md)
- [监控 UI Servlet 改造](docs/MONITOR-SERVLET.md)
- [Core 模块文档](securt-kit-core/README.md)
- [Core 使用文档](securt-kit-core/USAGE.md)

## 🔗 相关链接

- **项目主页**: https://github.com/genkidoudou/securt-kit
- **Wiki**: https://github.com/genkidoudou/securt-kit/wiki
- **问题反馈**: https://github.com/genkidoudou/securt-kit/issues
- **Maven Central 发布**: [docs/MAVEN-CENTRAL-PUBLISHING.md](docs/MAVEN-CENTRAL-PUBLISHING.md)

---

**最后更新**: 2026-09-13  
**项目版本**: v1.0-SNAPSHOT

