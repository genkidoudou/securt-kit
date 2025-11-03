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
- [技术架构](#技术架构)
- [使用指南](#使用指南)
- [API 文档](#api-文档)
- [最佳实践](#最佳实践)
- [常见问题](#常见问题)
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

### 5. 完善的测试

- **单表操作测试**：CRUD 操作验证
- **多表查询测试**：JOIN 查询验证
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
└── securt-kit-test/                  # 测试项目
    ├── pom.xml
    ├── README.md                     # 测试项目文档
    ├── API-DOCUMENTATION.md          # API 文档
    ├── TEST-GUIDE.md                 # 测试指南
    └── src/main/java/com/example/
        ├── TestApplication.java
        ├── entity/                   # 实体类
        ├── mapper/                   # Mapper 接口
        ├── dto/                      # DTO 类
        └── test/                     # 测试类
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

## 📚 使用指南

### 1. 基础配置

#### 配置文件方式（推荐）

```yaml
securtkit:
  encryptor:
    enable: true
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

## 📖 API 文档

### 核心类

#### FieldEncryptorProperties

字段加密配置属性类。

```java
public class FieldEncryptorProperties {
    private boolean enable;              // 是否启用
    private List<TableConfig> tables;    // 表配置列表
    
    public static class TableConfig {
        private String tableName;        // 表名
        private List<FieldConfig> fields; // 字段配置
    }
    
    public static class FieldConfig {
        private String fieldName;         // 字段名
        private String strategy;         // 加密策略类名（可选）
    }
}
```

#### TableCache

表配置缓存，管理加密配置。

```java
public class TableCache {
    // 初始化缓存
    public static void init(FieldEncryptorProperties properties);
    
    // 获取需要加密的表集合
    public static Set<String> getTables();
    
    // 获取字段加密策略
    public static Class<? extends FieldEncryptorStrategy> 
        getTableFieldEncryptInfo(String tableName, String fieldName);
}
```

#### SecurtkitUtils

SQL 解析工具类。

```java
public class SecurtkitUtils {
    // 解析 SQL，获取占位符映射和加密字段信息
    public static Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> 
        parseSql(String sql) throws JSQLParserException;
    
    // 判断表是否需要加密
    public static boolean needEncrypt(Collection<String> tables);
}
```

### 拦截器类

#### SimpleInterceptorDriver

JDBC 驱动拦截器，拦截数据库连接。

#### SimpleInterceptorPreparedStatement

预编译语句拦截器，处理参数加密。

#### ResultSetDecryptingProxy

结果集解密代理，处理查询结果解密。

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
- ✅ 自定义加密策略
- ✅ Spring Boot 自动配置

#### 模块
- ✅ securt-kit-core：核心功能模块
- ✅ securt-kit-starter：Spring Boot 启动器
- ✅ securt-kit-test：测试项目

#### 测试
- ✅ 单表操作测试
- ✅ 多表查询测试
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

### 项目结构
- [Core 源码](./securt-kit-core/src)
- [Starter 源码](./securt-kit-starter/src)
- [测试示例](./securt-kit-test/src)

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

