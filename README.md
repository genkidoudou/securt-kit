# Securt-Kit 数据库字段加密框架

[![Java Version](https://img.shields.io/badge/Java-1.8+-blue.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.6.13-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## 📋 目录

- [项目概述](#项目概述)
- [核心特性](#核心特性)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [模块说明](#模块说明)
  - [securt-kit-core](#securt-kit-core)
  - [securt-kit-starter](#securt-kit-starter)
  - [securt-kit-ui](#securt-kit-ui)
  - [securt-kit-test](#securt-kit-test)
- [技术架构](#技术架构)
- [使用指南](#使用指南)
- [使用文档和配置](#使用文档和配置)
  - [securt-kit-starter 使用文档](#securt-kit-starter-使用文档)
  - [securt-kit-ui 使用文档](#securt-kit-ui-使用文档)
- [API 文档](#api-文档)
- [最佳实践](#最佳实践)
- [常见问题](#常见问题)
- [文档](#文档)
- [版本历史](#版本历史)

---

## 📖 项目概述

**Securt-Kit** 是一个基于 JDBC 拦截技术的数据库字段加密解密框架，提供透明的数据库字段加密解密功能。该框架可以在不修改业务代码的情况下，对指定的数据库表和字段进行自动加密和解密。

### 设计理念

- **透明加密**：业务代码无需修改，框架自动处理加密解密
- **JDBC 拦截**：基于 JDBC 驱动代理技术，在数据库操作层面拦截和处理
- **灵活配置**：支持多种配置方式，按表和字段粒度配置
- **高性能**：优化的 SQL 解析和缓存机制，最小化性能影响

### 适用场景

- ✅ 敏感数据保护（身份证号、手机号、银行卡号等）
- ✅ 合规要求（GDPR、个人信息保护法等）
- ✅ 数据安全审计
- ✅ 数据库加密字段管理

---

## ✨ 核心特性

### 1. 透明加密解密

- **无需修改业务代码**：框架在 JDBC 层面自动拦截和处理
- **自动识别加密字段**：根据配置自动识别需要加密/解密的字段
- **支持复杂 SQL**：支持 JOIN、子查询、聚合函数等复杂场景

### 2. JDBC 驱动拦截

- **驱动代理**：实现自定义 JDBC 驱动，拦截数据库连接
- **多层包装**：包装 Connection、Statement、PreparedStatement、ResultSet 等对象
- **SQL 解析**：使用 JSQLParser 解析 SQL，识别表和字段

### 3. 灵活配置

- **多种配置方式**：支持 YAML、Properties、代码配置
- **按表配置**：可以为不同表配置不同的加密字段
- **自定义策略**：支持为每个字段配置不同的加密策略

### 4. 框架集成

- **Spring Boot 自动配置**：开箱即用的 Spring Boot Starter
- **MyBatis/MyBatis-Plus 支持**：完美集成 MyBatis 生态
- **数据库兼容**：支持 MySQL、PostgreSQL、Oracle、H2 等主流数据库

### 5. 多数据源支持

- **多数据源配置**：支持为不同数据源配置不同的加密字段
- **配置隔离**：不同数据源的加密配置相互独立
- **MyBatis-Plus 集成**：完美集成 dynamic-datasource
- **灵活配置**：支持在表配置中直接指定 `datasource-id`

### 6. 完善的测试

- **单表操作测试**：CRUD 操作验证
- **多表查询测试**：JOIN 查询验证
- **多数据源测试**：多数据源场景验证
- **复杂场景测试**：批量操作、事务、复杂查询等场景验证
- **集成测试**：完整的使用场景测试

---

## 📁 项目结构

```
securt-kit/
├── pom.xml                           # 父 POM，管理所有子模块
│
├── securt-kit-core/                  # 核心模块
│   ├── pom.xml
│   ├── README.md                     # Core 模块文档
│   ├── USAGE.md                      # 使用文档
│   └── src/main/java/
│       └── io/github/hexlodev/core/
│           ├── config/               # 配置类
│           │   └── FieldEncryptorProperties.java
│           ├── interceptor/          # JDBC 拦截器
│           │   ├── SimpleInterceptorDriver.java
│           │   ├── SimpleInterceptorConnection.java
│           │   ├── SimpleInterceptorStatement.java
│           │   ├── SimpleInterceptorPreparedStatement.java
│           │   ├── SimpleInterceptorCallableStatement.java
│           │   └── ResultSetDecryptingProxy.java
│           ├── parser/               # SQL 解析器
│           │   ├── SecurtkitUtils.java
│           │   ├── visitor/         # 访问者模式实现
│           │   └── dto/             # 数据传输对象
│           ├── strategy/             # 加密策略接口
│           │   └── FieldEncryptorStrategy.java
│           ├── TableCache.java          # 表配置缓存
│           └── utils/                # 工具类
│
├── securt-kit-starter/               # Spring Boot 启动器
│   ├── pom.xml
│   └── src/main/java/
│       └── io/github/hexlodev/config/
│           └── SecurtKitAutoConfiguration.java
│
├── securt-kit-ui/                    # 监控 UI 模块
│   ├── pom.xml
│   ├── SECURITY-PROTECTION-PLAN.md   # 安全防护方案
│   └── src/main/java/
│       └── io/github/hexlodev/ui/
│           ├── monitor/              # 监控控制器
│           │   └── MonitorController.java
│           └── security/             # 安全相关
│               └── SqlValidator.java
│
├── securt-kit-test/                  # 测试项目
│   ├── pom.xml
│   ├── README.md                     # 测试项目文档
│   ├── API-DOCUMENTATION.md          # API 文档
│   ├── TEST-GUIDE.md                 # 测试指南
│   └── src/main/java/com/example/
│       ├── TestApplication.java
│       ├── entity/                   # 实体类
│       ├── mapper/                   # Mapper 接口
│       ├── dto/                      # DTO 类
│       └── test/                     # 测试类
│
└── securt-kit-dy-datasource-test/    # 多数据源测试项目
    ├── pom.xml
    ├── README.md                     # 多数据源测试文档
    ├── COMPLEX-TEST-GUIDE.md         # 复杂场景测试指南
    └── src/main/java/io/github/test/
        ├── MultiDataSourceApplication.java
        ├── config/                   # 配置类
        ├── controller/               # 控制器
        ├── entity/                   # 实体类
        ├── mapper/                   # Mapper 接口
        └── service/                  # 服务类
```

---

## 🚀 快速开始

### 1. 添加依赖

#### Maven

```xml
<parent>
    <groupId>io.github.hexlodev</groupId>
    <artifactId>securt-kit</artifactId>
    <version>1.0-SNAPSHOT</version>
</parent>

<dependencies>
    <!-- Spring Boot Starter（推荐） -->
    <dependency>
        <groupId>io.github.hexlodev</groupId>
        <artifactId>securt-kit-starter</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    
    <!-- 或者直接使用 Core（需要手动配置） -->
    <dependency>
        <groupId>io.github.hexlodev.core</groupId>
        <artifactId>securt-kit-core</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

#### Gradle

```gradle
dependencies {
    implementation 'io.github.hexlodev:securt-kit-starter:1.0-SNAPSHOT'
}
```

### 2. 配置加密字段

#### application.yml

```yaml
securtkit:
  encryptor:
    enable: true
    tables:
      - table-name: user
        fields:
          - field-name: name
          - field-name: phone
      - table-name: orders
        fields:
          - field-name: customer_name
          - field-name: customer_phone
```

### 3. 实现加密策略

```java
@Component
public class MyFieldEncryptorStrategy implements FieldEncryptorStrategy {
    
    @Override
    public String encryption(String fieldValue) {
        // 实现加密逻辑
        return AESUtil.encrypt(fieldValue);
    }
    
    @Override
    public String decryption(String fieldValue) {
        // 实现解密逻辑
        return AESUtil.decrypt(fieldValue);
    }
}
```

### 4. 配置数据源

```yaml
spring:
  datasource:
    driver-class-name: io.github.hexlodev.core.interceptor.SimpleInterceptorDriver
    url: jdbc:interceptor:mysql://localhost:3306/test_db
    username: root
    password: your_password
```

### 5. 使用（无需修改业务代码）

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
}

// 业务代码
@Autowired
private UserMapper userMapper;

// 插入时自动加密
User user = new User();
user.setName("张三");      // 自动加密
user.setPhone("13800138000"); // 自动加密
userMapper.insert(user);

// 查询时自动解密
User user = userMapper.selectById(1L);
System.out.println(user.getName());  // 已自动解密
```

---

## 📦 模块说明

### securt-kit-core

**核心模块**，提供所有基础功能。

**主要组件**：

1. **拦截器层（interceptor）**
   - `SimpleInterceptorDriver`: JDBC 驱动拦截器
   - `SimpleInterceptorConnection`: 连接包装器
   - `SimpleInterceptorPreparedStatement`: 预编译语句包装器
   - `ResultSetDecryptingProxy`: 结果集解密代理

2. **解析器层（parser）**
   - `SecurtkitUtils`: SQL 解析工具类
   - `PoJoEncrtptorStatementVisitor`: SQL 访问者，提取字段信息
   - 各种 Visitor 实现：处理 SELECT、INSERT、UPDATE 等语句

3. **配置层（config）**
   - `FieldEncryptorProperties`: 字段加密配置属性类

4. **策略层（strategy）**
   - `FieldEncryptorStrategy`: 加密策略接口

**文档**: [securt-kit-core/README.md](./securt-kit-core/README.md)

---

### securt-kit-starter

**Spring Boot 启动器**，提供自动配置功能。

**功能**：
- 自动配置 `FieldEncryptorProperties`
- Spring Boot 开箱即用
- 自动初始化 `TableCache`

**使用方式**：

```xml
<dependency>
    <groupId>io.github.hexlodev</groupId>
    <artifactId>securt-kit-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

添加依赖后，框架会自动配置，无需额外代码。

---

### securt-kit-ui

**监控 UI 模块**，提供 Web 界面的监控和管理功能。

**主要功能**：

1. **加密解密测试**
   - 在线测试字段加密和解密功能
   - 支持自定义加密策略测试
   - 实时查看加密/解密结果

2. **SQL 解析**
   - 解析 SQL 语句，展示表名和字段信息
   - 识别需要加密的字段
   - 可视化 SQL 解析结果

3. **SQL 执行**
   - 在线执行 SQL 语句（仅支持 SELECT 查询）
   - 查看查询结果
   - 验证加密/解密功能

4. **安全防护**
   - XSS 防护
   - SQL 注入防护
   - CSRF 防护
   - 输入验证和过滤
   - 速率限制

**使用方式**：

```xml
<dependency>
    <groupId>io.github.hexlodev</groupId>
    <artifactId>securt-kit-ui</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**访问地址**：

启动应用后，访问 `http://localhost:8080/monitor/index.html` 即可使用监控界面。

**安全配置**：

UI 模块内置了完善的安全防护机制，详细说明请参考：
- [安全防护方案](./securt-kit-ui/SECURITY-PROTECTION-PLAN.md)

**注意事项**：

- ⚠️ 监控页面默认未启用认证，生产环境请务必配置访问控制
- ⚠️ 建议通过内网访问或配置反向代理
- ⚠️ SQL 查询功能仅支持 SELECT 语句，禁止执行 DML 操作

---

### securt-kit-test

**测试项目**，提供完整的测试示例。

**包含内容**：
- 单表操作测试
- 多表查询测试
- MyBatis/MyBatis-Plus 集成示例
- 完整的使用示例

**运行测试**：

```bash
cd securt-kit-test
mvn spring-boot:run
```

**文档**: [securt-kit-test/README.md](./securt-kit-test/README.md)

---

### securt-kit-dy-datasource-test

**多数据源测试项目**，提供完整的多数据源场景测试示例。

**包含内容**：
- 多数据源配置示例
- 差异化加密配置测试
- MyBatis-Plus dynamic-datasource 集成
- 复杂场景测试（批量操作、事务、复杂查询）
- 完整的 REST API 测试接口

**运行测试**：

```bash
cd securt-kit-dy-datasource-test
mvn spring-boot:run
```

**访问地址**：
- 基础测试：`http://localhost:8081/test/primary`
- 复杂场景测试：`http://localhost:8081/api/complex/*`

**文档**: 
- [securt-kit-dy-datasource-test/README.md](./securt-kit-dy-datasource-test/README.md) - 多数据源测试使用说明
- [securt-kit-dy-datasource-test/COMPLEX-TEST-GUIDE.md](./securt-kit-dy-datasource-test/COMPLEX-TEST-GUIDE.md) - 复杂场景测试指南

---

## 🏗️ 技术架构

### 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                     应用层 (Application)                      │
│  ┌────────────┐  ┌────────────┐  ┌──────────────────────┐  │
│  │ MyBatis    │  │ JdbcTemplate│  │  其他 ORM 框架        │  │
│  └────────────┘  └────────────┘  └──────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              Securt-Kit 拦截层 (Interceptor Layer)            │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  SimpleInterceptorDriver                               │  │
│  │  ┌──────────────────────────────────────────────────┐ │  │
│  │  │ SimpleInterceptorConnection                       │ │  │
│  │  │  ┌──────────────────────────────────────────────┐ │ │  │
│  │  │  │ SimpleInterceptorPreparedStatement         │ │ │  │
│  │  │  │  • 解析 SQL                                 │ │ │  │
│  │  │  │  • 识别加密字段                             │ │ │  │
│  │  │  │  • 字段加密                                 │ │ │  │
│  │  │  └──────────────────────────────────────────────┘ │ │  │
│  │  │  ┌──────────────────────────────────────────────┐ │ │  │
│  │  │  │ ResultSetDecryptingProxy                    │ │ │  │
│  │  │  │  • 字段解密                                 │ │ │  │
│  │  │  └──────────────────────────────────────────────┘ │ │  │
│  │  └──────────────────────────────────────────────────┘ │  │
│  └────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   解析层 (Parser Layer)                       │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ SecurtkitUtils                                          │  │
│  │  • SQL 解析（JSQLParser）                              │  │
│  │  • 表字段识别                                           │  │
│  │  • 占位符映射                                           │  │
│  └────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   配置层 (Config Layer)                       │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ TableCache                                              │  │
│  │  • 表配置缓存                                           │  │
│  │  • 字段加密策略管理                                     │  │
│  │                                                         │  │
│  │ FieldEncryptorProperties                                │  │
│  │  • 配置属性管理                                         │  │
│  └────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   策略层 (Strategy Layer)                   │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ FieldEncryptorStrategy                                  │  │
│  │  • encryption() - 加密方法                              │  │
│  │  • decryption() - 解密方法                              │  │
│  └────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   数据库层 (Database Layer)                   │
│          MySQL / PostgreSQL / Oracle / H2 / ...              │
└─────────────────────────────────────────────────────────────┘
```

### 工作流程

#### 插入流程（INSERT）

```
1. 应用调用 insert(user)
   ↓
2. MyBatis 生成 PreparedStatement
   ↓
3. SimpleInterceptorPreparedStatement 拦截
   ↓
4. SecurtkitUtils.parseSql() 解析 SQL
   ↓
5. 识别需要加密的字段（name, phone）
   ↓
6. 调用 FieldEncryptorStrategy.encryption() 加密
   ↓
7. 使用加密后的值执行 SQL
   ↓
8. 数据库存储加密数据
```

#### 查询流程（SELECT）

```
1. 应用调用 selectById(id)
   ↓
2. MyBatis 执行查询
   ↓
3. ResultSetDecryptingProxy 包装 ResultSet
   ↓
4. 识别需要解密的字段（name, phone）
   ↓
5. 调用 FieldEncryptorStrategy.decryption() 解密
   ↓
6. 返回解密后的数据给应用
```

---

## 📚 文档

### 核心文档

- [README.md](./README.md) - 项目主文档（本文件）
- [ARCHITECTURE.md](./ARCHITECTURE.md) - 架构设计文档
- [QUICK-START.md](./QUICK-START.md) - 快速开始指南
- [CONTRIBUTING.md](./CONTRIBUTING.md) - 贡献指南

### 模块文档

- [securt-kit-core/README.md](./securt-kit-core/README.md) - Core 模块文档
- [securt-kit-core/USAGE.md](./securt-kit-core/USAGE.md) - 使用文档
- [securt-kit-test/README.md](./securt-kit-test/README.md) - 测试项目文档
- [securt-kit-test/API-DOCUMENTATION.md](./securt-kit-test/API-DOCUMENTATION.md) - API 文档
- [securt-kit-test/TEST-GUIDE.md](./securt-kit-test/TEST-GUIDE.md) - 测试指南
- [securt-kit-dy-datasource-test/README.md](./securt-kit-dy-datasource-test/README.md) - 多数据源测试文档
- [securt-kit-dy-datasource-test/COMPLEX-TEST-GUIDE.md](./securt-kit-dy-datasource-test/COMPLEX-TEST-GUIDE.md) - 复杂场景测试指南
- [securt-kit-ui/SECURITY-PROTECTION-PLAN.md](./securt-kit-ui/SECURITY-PROTECTION-PLAN.md) - UI 模块安全防护方案

### 设计文档

- [ARCHITECTURE.md](./ARCHITECTURE.md) - 架构设计
- [MONITOR-DESIGN.md](./MONITOR-DESIGN.md) - 监控页面设计
- [MONITOR-IMPLEMENTATION-DESIGN.md](./MONITOR-IMPLEMENTATION-DESIGN.md) - 监控实现设计
- [ENCRYPT-DECRYPT-MONITOR-DESIGN.md](./ENCRYPT-DECRYPT-MONITOR-DESIGN.md) - 加密解密监控设计
- [DRUID-MONITOR-ANALYSIS.md](./DRUID-MONITOR-ANALYSIS.md) - Druid 监控分析

### 优化和安全文档

- [OPTIMIZATION-ANALYSIS.md](./OPTIMIZATION-ANALYSIS.md) - 优化分析报告
- [COMPREHENSIVE-OPTIMIZATION-REPORT.md](./COMPREHENSIVE-OPTIMIZATION-REPORT.md) - 全面优化分析报告
- [securt-kit-ui/SECURITY-PROTECTION-PLAN.md](./securt-kit-ui/SECURITY-PROTECTION-PLAN.md) - 安全防护方案

### 代码规范文档

- [CODE-COMMENT-GUIDE.md](./CODE-COMMENT-GUIDE.md) - 代码注释规范指南
- [CODE-COMMENT-SUMMARY.md](./CODE-COMMENT-SUMMARY.md) - 代码注释完善总结
- [CONTRIBUTING.md](./CONTRIBUTING.md) - 贡献指南（包含代码规范）

### 文档索引

- [DOCS-INDEX.md](./DOCS-INDEX.md) - 文档索引

### 1. 基础配置

#### 配置文件方式（推荐）

```yaml
securtkit:
  encryptor:
    enable: true
    # 失败处理策略：FALLBACK（默认，推荐）、FAIL_FAST、RETRY、SKIP
    failure-policy: FALLBACK
    # SQL 解析缓存配置（可选）
    sql-parse-cache:
      enable: true
      max-size: 1000
    tables:
      - table-name: user
        fields:
          - field-name: name
          - field-name: phone
            strategy: com.example.MyCustomStrategy  # 可选：自定义策略
```

#### 代码配置方式

```java
@Configuration
public class SecurtKitConfig {
    
    @Bean
    public FieldEncryptorProperties fieldEncryptorProperties() {
        FieldEncryptorProperties props = new FieldEncryptorProperties();
        props.setEnable(true);
        
        // 配置表
        FieldEncryptorProperties.TableConfig tableConfig = 
            new FieldEncryptorProperties.TableConfig();
        tableConfig.setTableName("user");
        
        // 配置字段
        FieldEncryptorProperties.FieldConfig nameField = 
            new FieldEncryptorProperties.FieldConfig();
        nameField.setFieldName("name");
        
        tableConfig.setFields(Arrays.asList(nameField));
        props.setTables(Arrays.asList(tableConfig));
        
        // 初始化缓存
        TableCache.init(props);
        
        return props;
    }
}
```

### 2. 数据源配置

```yaml
spring:
  datasource:
    # 使用拦截器驱动
    driver-class-name: io.github.hexlodev.core.interceptor.SimpleInterceptorDriver
    # URL 格式：jdbc:interceptor:{真实数据库URL}
    url: jdbc:interceptor:mysql://localhost:3306/test_db
    username: root
    password: password
```

### 3. 自定义加密策略

```java
@Component
public class AESFieldEncryptorStrategy implements FieldEncryptorStrategy {
    
    private static final String ALGORITHM = "AES";
    private static final String KEY = "MySecretKey12345"; // 实际应该从配置读取
    
    @Override
    public String encryption(String fieldValue) {
        if (fieldValue == null) {
            return null;
        }
        try {
            // AES 加密实现
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(KEY.getBytes(), ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(fieldValue.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }
    
    @Override
    public String decryption(String fieldValue) {
        if (fieldValue == null) {
            return null;
        }
        try {
            // AES 解密实现
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(KEY.getBytes(), ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.decrypt(Base64.getDecoder().decode(fieldValue));
            return new String(decrypted);
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }
}
```

### 4. MyBatis 使用

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // MyBatis-Plus 提供的基础方法即可，无需修改
}

@Service
public class UserService {
    
    @Autowired
    private UserMapper userMapper;
    
    public void createUser(User user) {
        // name 和 phone 会自动加密
        userMapper.insert(user);
    }
    
    public User getUser(Long id) {
        // name 和 phone 会自动解密
        return userMapper.selectById(id);
    }
}
```

### 5. 多表查询

```java
@Mapper
public interface UserOrderMapper {
    
    @Select("SELECT u.name, u.phone, o.order_no, o.customer_name " +
            "FROM user u INNER JOIN orders o ON u.id = o.user_id " +
            "WHERE u.id = #{userId}")
    List<UserOrderDTO> selectUserOrders(@Param("userId") Long userId);
}

// 使用
List<UserOrderDTO> orders = userOrderMapper.selectUserOrders(1L);
// u.name, u.phone, o.customer_name 都会自动解密
```

---

## 📚 使用文档和配置

### securt-kit-starter 使用文档

**securt-kit-starter** 是 Spring Boot 启动器模块，提供开箱即用的自动配置功能。

#### 1. 添加依赖

**Maven**：

```xml
<dependency>
    <groupId>io.github.hexlodev</groupId>
    <artifactId>securt-kit-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**Gradle**：

```gradle
dependencies {
    implementation 'io.github.hexlodev:securt-kit-starter:1.0-SNAPSHOT'
}
```

#### 2. 配置文件

在 `application.yml` 或 `application.properties` 中配置加密字段：

**YAML 配置（推荐）**：

```yaml
securtkit:
  encryptor:
    # 是否启用加密功能（默认：true）
    enable: true
    
    # 失败处理策略
    # FAIL_FAST: 加密/解密失败时立即抛出异常
    # FALLBACK: 加密/解密失败时返回原值（默认，推荐）
    # RETRY: 加密/解密失败时重试（暂未实现）
    # SKIP: 加密/解密失败时跳过
    failure-policy: FALLBACK
    
    # SQL 解析缓存配置（可选）
    sql-parse-cache:
      # 是否启用缓存（默认：true）
      enable: true
      # 缓存最大容量（默认：1000）
      max-size: 1000
    
    # 表配置列表
    tables:
      # 用户表配置
      - table-name: user
        fields:
          # 姓名字段（使用默认策略）
          - field-name: name
          # 手机号字段（使用自定义策略）
          - field-name: phone
            strategy: com.example.CustomPhoneStrategy
      
      # 订单表配置
      - table-name: orders
        fields:
          - field-name: customer_name
          - field-name: customer_phone
            strategy: com.example.CustomPhoneStrategy
```

**Properties 配置**：

```properties
# 启用加密功能
securtkit.encryptor.enable=true

# 失败处理策略
securtkit.encryptor.failure-policy=FALLBACK

# SQL 解析缓存配置
securtkit.encryptor.sql-parse-cache.enable=true
securtkit.encryptor.sql-parse-cache.max-size=1000

# 表配置
securtkit.encryptor.tables[0].table-name=user
securtkit.encryptor.tables[0].fields[0].field-name=name
securtkit.encryptor.tables[0].fields[1].field-name=phone
securtkit.encryptor.tables[0].fields[1].strategy=com.example.CustomPhoneStrategy
```

#### 3. 数据源配置

配置数据源使用拦截器驱动：

```yaml
spring:
  datasource:
    # 使用拦截器驱动
    driver-class-name: io.github.hexlodev.core.interceptor.SimpleInterceptorDriver
    # URL 格式：jdbc:interceptor:{真实数据库URL}
    url: jdbc:interceptor:mysql://localhost:3306/test_db?useUnicode=true&characterEncoding=utf8
    username: root
    password: your_password
    type: com.zaxxer.hikari.HikariDataSource
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
```

**支持的数据库 URL 格式**：

- MySQL: `jdbc:interceptor:mysql://localhost:3306/dbname`
- PostgreSQL: `jdbc:interceptor:postgresql://localhost:5432/dbname`
- Oracle: `jdbc:interceptor:oracle:thin:@localhost:1521:orcl`
- H2: `jdbc:interceptor:h2:mem:testdb`
- SQL Server: `jdbc:interceptor:sqlserver://localhost:1433;databaseName=dbname`

#### 4. 实现加密策略

创建加密策略实现类：

```java
package com.example;

import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 自定义加密策略示例
 * 使用 AES 算法进行加密/解密
 */
@Component
public class AESFieldEncryptorStrategy implements FieldEncryptorStrategy {
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    
    // 实际项目中应该从配置中心或环境变量读取密钥
    private static final String SECRET_KEY = "YourSecretKey12345678901234567890"; // 32字节
    
    @Override
    public String encryption(String oldValue) {
        if (oldValue == null) {
            return null;
        }
        try {
            // AES 加密实现
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(oldValue.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("加密失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String decryption(String oldValue) {
        if (oldValue == null) {
            return null;
        }
        try {
            // AES 解密实现
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.decrypt(Base64.getDecoder().decode(oldValue));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("解密失败: " + e.getMessage(), e);
        }
    }
}
```

#### 5. 自动配置说明

**securt-kit-starter** 会自动完成以下配置：

1. **自动加载配置**：从 `application.yml` 或 `application.properties` 读取配置
2. **自动初始化 TableCache**：启动时初始化表配置缓存
3. **自动配置 SQL 解析缓存**：根据配置初始化 SQL 解析缓存
4. **自动扫描加密策略**：自动扫描 Spring 容器中的 `FieldEncryptorStrategy` 实现

**无需手动配置**，添加依赖后即可使用！

#### 6. 验证配置

启动应用后，查看日志确认配置是否生效：

```
INFO  io.github.hexlodev.core.interceptor.SimpleInterceptorDriver - SimpleInterceptorDriver registered successfully
INFO  io.github.hexlodev.core.TableCache - Table cache initialized: 2 tables, 4 fields
```

---

### securt-kit-ui 使用文档

**securt-kit-ui** 是监控 UI 模块，提供 Web 界面的监控和管理功能。

#### 1. 添加依赖

**Maven**：

```xml
<dependency>
    <groupId>io.github.hexlodev</groupId>
    <artifactId>securt-kit-ui</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**Gradle**：

```gradle
dependencies {
    implementation 'io.github.hexlodev:securt-kit-ui:1.0-SNAPSHOT'
}
```

#### 2. 启用 UI 模块

添加依赖后，UI 模块会自动配置，无需额外配置。

**访问地址**：

启动应用后，访问以下地址即可使用监控界面：

```
http://localhost:8080/monitor/index.html
```

**注意**：端口号根据实际应用配置调整。

#### 3. 功能说明

##### 3.1 加密解密测试

**功能**：在线测试字段加密和解密功能

**使用方式**：
1. 访问监控页面
2. 切换到"加密解密"标签页
3. 输入原始值（明文）
4. 选择加密策略（如果配置了多个策略）
5. 点击"加密"按钮查看加密结果
6. 点击"解密"按钮查看解密结果

**示例**：
- 输入：`13800138000`
- 加密后：`base64_encoded_string`
- 解密后：`13800138000`

##### 3.2 SQL 解析

**功能**：解析 SQL 语句，展示表名和字段信息

**使用方式**：
1. 切换到"SQL 解析"标签页
2. 输入 SQL 语句（支持 SELECT、INSERT、UPDATE、DELETE）
3. 点击"解析"按钮
4. 查看解析结果：
   - 识别到的表名
   - 识别到的字段名
   - 需要加密的字段列表
   - 占位符映射关系

**示例 SQL**：
```sql
INSERT INTO user(name, phone) VALUES(?, ?)
```

**解析结果**：
- 表名：`user`
- 字段：`name`, `phone`
- 需要加密：`name`, `phone`

##### 3.3 SQL 执行

**功能**：在线执行 SQL 查询语句（仅支持 SELECT）

**使用方式**：
1. 切换到"SQL 执行"标签页
2. 输入 SELECT 查询语句
3. 填写查询参数（如果有）
4. 点击"执行"按钮
5. 查看查询结果（自动解密）

**安全限制**：
- ⚠️ **仅支持 SELECT 查询**，禁止执行 INSERT、UPDATE、DELETE 等 DML 操作
- ⚠️ SQL 语句会经过严格的验证和过滤
- ⚠️ 防止 SQL 注入攻击

**示例 SQL**：
```sql
SELECT id, name, phone FROM user WHERE id = ?
```

#### 4. 安全配置

UI 模块内置了完善的安全防护机制：

##### 4.1 XSS 防护

- 所有用户输入都会进行 HTML 转义
- 防止跨站脚本攻击
- 输出内容自动转义

##### 4.2 SQL 注入防护

- SQL 语句验证和过滤
- 使用 `SqlValidator` 验证 SQL 语法
- 禁止执行非 SELECT 语句
- 参数化查询支持

##### 4.3 CSRF 防护

- 自动生成 CSRF Token
- 请求验证 CSRF Token
- 防止跨站请求伪造

##### 4.4 输入验证

- 输入长度限制
- SQL 语句长度限制（默认 5000 字符）
- 参数数量限制
- 特殊字符过滤

##### 4.5 速率限制

- 防止暴力破解
- 限制请求频率
- IP 级别的速率限制

**详细安全方案**：参考 [安全防护方案](./securt-kit-ui/SECURITY-PROTECTION-PLAN.md)

#### 5. 生产环境配置

##### 5.1 访问控制

**⚠️ 重要**：监控页面默认未启用认证，生产环境请务必配置访问控制！

**推荐方案**：

**方案1：配置 Spring Security**

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .antMatchers("/monitor/**").hasRole("ADMIN") // 限制管理员访问
                .anyRequest().permitAll()
            .and()
            .httpBasic(); // 使用 HTTP Basic 认证
    }
}
```

**方案2：配置反向代理**

使用 Nginx 等反向代理服务器，配置访问控制：

```nginx
location /monitor {
    # 仅允许内网访问
    allow 192.168.0.0/16;
    allow 10.0.0.0/8;
    deny all;
    
    proxy_pass http://localhost:8080;
}
```

**方案3：关闭 UI 模块**

如果不使用监控功能，可以不添加 `securt-kit-ui` 依赖。

##### 5.2 网络安全

- ✅ 建议通过内网访问
- ✅ 使用 HTTPS 加密传输
- ✅ 配置防火墙规则
- ✅ 限制访问 IP 范围

##### 5.3 日志配置

建议配置日志记录，监控访问情况：

```yaml
logging:
  level:
    io.github.hexlodev.ui.monitor: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

#### 6. 常见问题

**Q: 访问监控页面出现 404？**

A: 检查：
1. 是否正确添加了 `securt-kit-ui` 依赖
2. 访问地址是否正确：`http://localhost:8080/monitor/index.html`
3. 应用是否正常启动
4. 查看启动日志是否有错误

**Q: SQL 执行功能无法使用？**

A: 检查：
1. 是否配置了数据源
2. SQL 语句是否为 SELECT 查询
3. SQL 语句是否符合语法规范
4. 查看浏览器控制台是否有错误信息

**Q: 加密解密测试功能无法使用？**

A: 检查：
1. 是否配置了加密策略
2. 加密策略是否正确注册为 Spring Bean
3. 查看浏览器控制台是否有错误信息

**Q: 如何禁用 UI 模块？**

A: 不添加 `securt-kit-ui` 依赖即可，或者通过配置排除：

```java
@SpringBootApplication(exclude = {
    // 排除 UI 模块的自动配置
})
public class Application {
    // ...
}
```

#### 7. API 接口说明

UI 模块提供了以下 REST API 接口：

**加密解密接口**：
- `POST /monitor/api/encrypt` - 加密
- `POST /monitor/api/decrypt` - 解密

**SQL 解析接口**：
- `POST /monitor/api/parse` - 解析 SQL

**SQL 执行接口**：
- `POST /monitor/api/query` - 执行查询

**详细 API 文档**：参考 UI 模块源代码或查看浏览器开发者工具的 Network 面板。

---

## 📖 API 文档

### 核心类

#### FieldEncryptorProperties

字段加密配置属性类，用于读取和绑定配置文件中的加密配置。

**主要属性：**
- `enable`: 是否启用加密功能
- `tables`: 表配置列表，每个表配置包含表名和字段列表
- `failurePolicy`: 失败处理策略（FAIL_FAST、FALLBACK、RETRY、SKIP）
- `sqlParseCache`: SQL 解析缓存配置

**配置示例：**
```yaml
securtkit:
  encryptor:
    enable: true
    failure-policy: FALLBACK
    sql-parse-cache:
      enable: true
      max-size: 1000
    tables:
      - table-name: user
        fields:
          - field-name: name
          - field-name: phone
            strategy: com.example.CustomStrategy
```

#### TableCache

表配置缓存，管理加密配置并提供快速查找功能。

**关键方法：**
```java
// 初始化缓存（启动时调用）
public static void init(FieldEncryptorProperties properties);

// 获取需要加密的表集合
public static Set<String> getTables();

// 获取字段加密策略类
public static Class<? extends FieldEncryptorStrategy> 
    getTableFieldEncryptInfo(String tableName, String fieldName);
    
// 检查表是否需要加密
public static boolean concatTable(String tableName);
```

**性能优化：**
- 使用线程安全的集合实现
- 返回不可变视图，防止外部修改
- 支持表名规范化（自动提取纯表名）

#### SecurtkitUtils

SQL 解析工具类，提供 SQL 解析和字段识别功能。

**关键方法：**
```java
// 解析 SQL，获取占位符映射和加密字段信息
public static Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> 
    parseSql(String sql) throws JSQLParserException;
    
// 判断表是否需要加密
public static boolean needEncrypt(Collection<String> tables);
```

**性能优化：**
- 使用缓存机制，相同 SQL 的解析结果会被缓存
- 支持 SQL 规范化，提升缓存命中率
- 使用 LRU 算法自动淘汰最久未使用的条目

#### FieldEncryptorStrategy

字段加密解密策略接口，所有加密策略都必须实现此接口。

**接口方法：**
```java
// 加密方法
String encryption(String oldValue);

// 解密方法
String decryption(String oldValue);
```

**实现要求：**
- 加密和解密方法必须互逆
- 必须处理 null 值
- 应该处理异常情况
- 建议实现为线程安全的

### 拦截器类

#### SimpleInterceptorDriver

JDBC 驱动拦截器，拦截数据库连接请求。

**主要功能：**
- 自动注册到 DriverManager
- 识别拦截器URL格式：`jdbc:interceptor:<real-url>`
- 提取真实数据库URL并查找对应的底层驱动
- 包装数据库连接以支持拦截功能

**使用方式：**
```yaml
spring:
  datasource:
    driver-class-name: io.github.hexlodev.core.interceptor.SimpleInterceptorDriver
    url: jdbc:interceptor:mysql://localhost:3306/test_db
```

#### SimpleInterceptorPreparedStatement

预编译语句拦截器，处理参数加密。

**主要功能：**
- 拦截 PreparedStatement 的参数设置方法
- 对需要加密的字段值进行加密处理
- 记录 SQL 日志和性能指标
- 性能优化：使用 O(1) Map 查找替代 O(n) Stream 查找

**性能优化：**
- 在构造函数中构建参数索引到字段的映射（`Map<Integer, ColumnTableDto>`）
- 将 `setString` 方法中的查找操作从 O(n) 优化为 O(1)
- 预计性能提升 30-50%（高频调用场景）

#### ResultSetDecryptingProxy

结果集解密代理，处理查询结果解密。

**主要功能：**
- 使用动态代理包装 ResultSet
- 拦截字段读取方法（getString、getObject 等）
- 对需要解密的字段值进行解密处理
- 性能优化：使用 O(1) Map 查找替代 O(n) Stream 查找

**性能优化：**
- 在构造函数中构建列名到字段信息的映射（`Map<String, FieldEncryptorInfoDto>`）
- 将解密方法中的查找操作从 O(n) 优化为 O(1)
- 改进异常处理：添加调试日志，便于问题排查

**详细 API 文档**: [securt-kit-test/API-DOCUMENTATION.md](./securt-kit-test/API-DOCUMENTATION.md)

---

## 🎯 最佳实践

### 1. 密钥管理

**❌ 错误示例**：

```java
// 硬编码密钥
private static final String KEY = "MySecretKey12345";
```

**✅ 正确示例**：

```java
// 从环境变量或配置中心读取
@Value("${encrypt.key}")
private String encryptKey;

// 或使用密钥管理服务
@Autowired
private KeyManagementService keyService;
```

### 2. 性能优化

- **只加密敏感字段**：不要对所有字段都加密
- **使用缓存**：TableCache 已经提供了配置缓存
- **批量操作**：优先使用批量插入/更新

### 3. 安全建议

- **使用强加密算法**：推荐 AES-256
- **密钥轮换**：定期更换加密密钥
- **审计日志**：记录加密解密操作日志

### 4. 错误处理

```java
@Component
public class SafeFieldEncryptorStrategy implements FieldEncryptorStrategy {
    
    @Override
    public String encryption(String fieldValue) {
        try {
            return doEncrypt(fieldValue);
        } catch (Exception e) {
            log.error("加密失败", e);
            // 根据业务需求决定是抛出异常还是返回原值
            throw new EncryptionException("加密失败", e);
        }
    }
}
```

---

## ❓ 常见问题

### Q1: 如何添加新的加密字段？

在 `application.yml` 中配置：

```yaml
securtkit:
  encryptor:
    tables:
      - table-name: your_table
        fields:
          - field-name: your_field
```

### Q2: 多表查询时字段没有自动解密？

检查：
1. 字段是否在配置中正确声明
2. Mapper XML 中的字段别名是否正确
3. resultMap 是否正确映射了所有字段

### Q3: 如何自定义加密策略？

实现 `FieldEncryptorStrategy` 接口，并配置到字段：

```yaml
fields:
  - field-name: name
    strategy: com.example.MyCustomStrategy
```

### Q4: 支持哪些数据库？

理论上支持所有 JDBC 兼容的数据库：
- MySQL
- PostgreSQL
- Oracle
- SQL Server
- H2
- SQLite
- 等

### Q5: 性能影响如何？

- 加密解密操作在内存中进行，性能影响较小
- SQL 解析使用缓存，不会重复解析
- 建议只对敏感字段加密，避免影响性能

更多问题请查看：[securt-kit-core/USAGE.md](./securt-kit-core/USAGE.md#常见问题)

---

## 🔄 版本历史

### v1.0-SNAPSHOT (当前版本)

#### 核心功能
- ✅ JDBC 驱动拦截
- ✅ PreparedStatement 参数加密
- ✅ ResultSet 结果解密
- ✅ SQL 解析与表字段识别
- ✅ 多表查询支持
- ✅ 多数据源支持
- ✅ 自定义加密策略
- ✅ Spring Boot 自动配置

#### 模块
- ✅ securt-kit-core：核心功能模块
- ✅ securt-kit-starter：Spring Boot 启动器
- ✅ securt-kit-test：测试项目
- ✅ securt-kit-dy-datasource-test：多数据源测试项目

#### 测试
- ✅ 单表操作测试
- ✅ 多表查询测试
- ✅ 多数据源测试
- ✅ 复杂场景测试（批量操作、事务、复杂查询）
- ✅ 集成测试

---

## 📝 开发指南

### 构建项目

```bash
# 编译所有模块
mvn clean install

# 跳过测试
mvn clean install -DskipTests

# 只编译特定模块
cd securt-kit-core
mvn clean install
```

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行测试项目
cd securt-kit-test
mvn spring-boot:run
```

### 贡献代码

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

---

## 📄 许可证

本项目采用 **Apache License 2.0** 许可证。

详见 [LICENSE](LICENSE) 文件。

---

## 🔗 相关链接

### 文档
- [Core 模块文档](./securt-kit-core/README.md)
- [使用文档](./securt-kit-core/USAGE.md)
- [测试项目文档](./securt-kit-test/README.md)
- [API 文档](./securt-kit-test/API-DOCUMENTATION.md)
- [测试指南](./securt-kit-test/TEST-GUIDE.md)
- [多数据源测试文档](./securt-kit-dy-datasource-test/README.md)
- [复杂场景测试指南](./securt-kit-dy-datasource-test/COMPLEX-TEST-GUIDE.md)

### 项目结构
- [Core 源码](./securt-kit-core/src)
- [Starter 源码](./securt-kit-starter/src)
- [测试示例](./securt-kit-test/src)
- [多数据源测试示例](./securt-kit-dy-datasource-test/src)

---

## 👥 贡献者

感谢所有为项目做出贡献的开发者！

---

## 📞 联系方式

- **项目主页**: https://github.com/hexlodev/securt-kit
- **问题反馈**: https://github.com/hexlodev/securt-kit/issues
- **讨论区**: https://github.com/hexlodev/securt-kit/discussions

---

## 🙏 致谢

本项目参考了以下优秀项目的设计理念：
- [P6Spy](https://github.com/p6spy/p6spy) - JDBC 拦截技术
- [JSQLParser](https://github.com/JSQLParser/JSqlParser) - SQL 解析器

---

**最后更新**: 2025-01-XX  
**项目版本**: v1.0-SNAPSHOT

