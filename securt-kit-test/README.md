# Securt-Kit 数据库字段加密框架

## 项目状态

✅ **已完成的功能**：
- 核心加密解密工具类 (`CryptoUtils`)
- 配置管理系统 (`FieldCryptoConfig`)
- SQL处理器 (`SqlCryptoProcessor`)
- JDBC驱动拦截 (`CryptoJdbcDriver`)
- 完整的包装器类体系
- Spring Boot集成示例
- 测试用例

⚠️ **当前问题**：
- JDBC接口方法实现不完整（需要实现所有抽象方法）
- 编译时出现"未覆盖抽象方法"错误

## 快速演示

### 1. 运行简化演示

```bash
cd securt-kit-test
mvn compile exec:java -Dexec.mainClass="io.github.test.demo.SimpleCryptoDemo"
```

### 2. 核心功能验证

```java
// 加密解密
String encrypted = CryptoUtils.encrypt("敏感数据", "MySecretKey12345");
String decrypted = CryptoUtils.decrypt(encrypted, "MySecretKey12345");

// 配置管理
FieldCryptoConfig config = new FieldCryptoConfig();
config.addEncryptField("users", "name");
config.addDecryptField("users", "name");
```

## 架构设计

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   应用代码      │    │   CryptoJdbcDriver │    │   真实数据库     │
│                 │    │                  │    │                 │
│ Connection conn │───▶│ 拦截JDBC调用     │───▶│ MySQL/PostgreSQL│
│ PreparedStatement│    │ 自动加密解密     │    │ H2/SQLite等     │
│ ResultSet rs    │◀───│ 透明处理         │◀───│                 │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

## 核心组件

### 1. CryptoJdbcDriver
- JDBC驱动代理，拦截数据库连接
- 自动包装Connection、Statement、ResultSet等对象

### 2. 包装器类
- `CryptoConnectionWrapper`: 连接包装器
- `CryptoStatementWrapper`: 语句包装器  
- `CryptoPreparedStatementWrapper`: 预编译语句包装器
- `CryptoResultSetWrapper`: 结果集包装器

### 3. 配置管理
- `FieldCryptoConfig`: 字段加密配置
- 支持多种配置方式（代码、配置文件、环境变量）

### 4. 加密工具
- `CryptoUtils`: AES加密解密工具
- `SqlCryptoProcessor`: SQL处理器

## 使用方式

### 基本使用

```java
// 1. 初始化配置
FieldCryptoConfig config = new FieldCryptoConfig();
config.addEncryptField("users", "name");
config.addDecryptField("users", "name");
config.setKey("MySecretKey12345");
CryptoJdbcDriver.initialize(config, config.getKey());

// 2. 使用加密驱动
String url = "jdbc:crypto:mysql://localhost:3306/test_db";
Connection conn = DriverManager.getConnection(url, username, password);

// 3. 正常使用JDBC API
PreparedStatement stmt = conn.prepareStatement("INSERT INTO users (name) VALUES (?)");
stmt.setString(1, "张三"); // 自动加密
stmt.executeUpdate();
```

### Spring Boot集成

```java
@Configuration
public class CryptoDataSourceConfig {
    @Bean
    public DataSource cryptoDataSource() {
        // 配置加密数据源
        return new CryptoDataSource();
    }
}
```

## 测试项目

### 1. 简化演示
- `SimpleCryptoDemo.java`: 核心功能演示
- 不依赖完整的JDBC包装器

### 2. Spring Boot测试
- `CryptoJdbcIntegrationTest.java`: 集成测试
- `SpringBootCryptoTest.java`: Spring Boot测试
- `UserController.java`: REST API测试

### 3. Web界面测试
- 访问 `http://localhost:8080/test.html`
- 提供完整的CRUD操作测试界面

## 配置示例

### application.properties
```properties
# 加密配置
crypto.algorithm=AES
crypto.key=MySecretKey12345
crypto.encrypt.fields=users.name,users.email,users.phone
crypto.decrypt.fields=users.name,users.email,users.phone

# 日志配置
logging.level.io.github.hexlodev.core.crypto=DEBUG
```

## 下一步计划

1. **完善JDBC接口实现**
   - 实现所有缺失的抽象方法
   - 确保编译通过

2. **增强SQL解析**
   - 集成JSQLParser进行更精确的SQL解析
   - 支持复杂SQL语句

3. **性能优化**
   - 添加缓存机制
   - 优化加密解密性能

4. **扩展功能**
   - 支持更多加密算法
   - 添加审计日志
   - 支持字段级权限控制

## 技术特点

- **透明加密**: 无需修改应用代码
- **JDBC拦截**: 基于驱动代理技术
- **灵活配置**: 支持多种配置方式
- **高性能**: 优化的加密算法
- **易于集成**: 简单的API设计

## 参考项目

本项目参考了 [P6Spy](https://github.com/p6spy/p6spy) 的JDBC拦截机制，实现了数据库字段的透明加密解密功能。
