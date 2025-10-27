# Securt-Kit Core - 数据库字段加密解密框架

## 项目简介

Securt-Kit Core 是一个基于JDBC拦截技术的数据库字段加密解密框架，参考了P6Spy的设计理念，提供透明的数据库字段加密解密功能。该框架可以在不修改应用代码的情况下，对指定的数据库表和字段进行自动加密和解密。

## 核心特性

- **透明加密解密**: 无需修改应用代码，自动对指定字段进行加密和解密
- **JDBC驱动拦截**: 基于JDBC驱动代理技术，拦截数据库操作
- **灵活配置**: 支持多种配置方式（配置文件、环境变量、系统属性）
- **高性能**: 优化的加密算法和缓存机制
- **易于集成**: 支持Spring Boot等主流框架
- **安全可靠**: 使用AES等标准加密算法

## 技术架构

### 核心组件

1. **CryptoJdbcDriver**: 加密JDBC驱动，拦截数据库连接请求
2. **CryptoConnectionWrapper**: 连接包装器，包装真实的数据库连接
3. **CryptoStatementWrapper**: 语句包装器，处理SQL语句的加密
4. **CryptoPreparedStatementWrapper**: 预编译语句包装器
5. **CryptoCallableStatementWrapper**: 存储过程语句包装器
6. **CryptoResultSetWrapper**: 结果集包装器，处理查询结果的解密
7. **FieldCryptoConfig**: 字段加密配置管理
8. **CryptoUtils**: 加密解密工具类
9. **SqlCryptoProcessor**: SQL处理器，解析和修改SQL语句

### 设计模式

- **装饰器模式**: 包装JDBC对象，添加加密解密功能
- **代理模式**: JDBC驱动代理，拦截数据库操作
- **工厂模式**: 创建加密解密处理器
- **策略模式**: 支持多种加密算法

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.hexlodev</groupId>
    <artifactId>securt-kit-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置加密字段

```java
// 创建字段加密配置
FieldCryptoConfig config = new FieldCryptoConfig();

// 配置需要加密的字段
config.addEncryptField("users", "name");
config.addEncryptField("users", "email");
config.addEncryptField("users", "phone");

// 配置需要解密的字段
config.addDecryptField("users", "name");
config.addDecryptField("users", "email");
config.addDecryptField("users", "phone");

// 设置加密算法和密钥
config.setAlgorithm("AES");
config.setKey("MySecretKey12345");
config.setTransformation("AES/CBC/PKCS5Padding");

// 初始化加密驱动
CryptoJdbcDriver.initialize(config, config.getKey());
```

### 3. 使用加密驱动

```java
// 使用加密驱动连接数据库
String url = "jdbc:crypto:mysql://localhost:3306/test_db";
String username = "root";
String password = "password";

Connection connection = DriverManager.getConnection(url, username, password);

// 正常使用JDBC API
PreparedStatement statement = connection.prepareStatement(
    "INSERT INTO users (name, email, phone) VALUES (?, ?, ?)");
statement.setString(1, "张三");
statement.setString(2, "zhangsan@example.com");
statement.setString(3, "13800138000");
statement.executeUpdate();

// 查询时自动解密
ResultSet resultSet = statement.executeQuery("SELECT name, email, phone FROM users");
while (resultSet.next()) {
    String name = resultSet.getString("name"); // 自动解密
    String email = resultSet.getString("email"); // 自动解密
    String phone = resultSet.getString("phone"); // 自动解密
}
```

## 配置方式

### 1. 配置文件方式

创建 `crypto-config.properties` 文件：

```properties
# 加密算法配置
crypto.algorithm=AES
crypto.key=MySecretKey12345
crypto.transformation=AES/CBC/PKCS5Padding

# 需要加密的字段配置
crypto.encrypt.fields=users.name,users.email,users.phone,orders.customer_name

# 需要解密的字段配置
crypto.decrypt.fields=users.name,users.email,users.phone,orders.customer_name
```

### 2. 环境变量方式

```bash
export CRYPTO_ALGORITHM=AES
export CRYPTO_KEY=MySecretKey12345
export CRYPTO_ENCRYPT_FIELDS=users.name,users.email,users.phone
export CRYPTO_DECRYPT_FIELDS=users.name,users.email,users.phone
```

### 3. 系统属性方式

```bash
java -Dcrypto.algorithm=AES \
     -Dcrypto.key=MySecretKey12345 \
     -Dcrypto.encrypt.fields=users.name,users.email,users.phone \
     -Dcrypto.decrypt.fields=users.name,users.email,users.phone \
     -jar your-application.jar
```

## Spring Boot 集成

### 1. 配置数据源

```java
@Configuration
public class DataSourceConfig {
    
    @Bean
    public DataSource dataSource() {
        // 初始化加密配置
        initializeCryptoConfig();
        
        // 创建数据源
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("io.github.hexlodev.core.crypto.CryptoJdbcDriver");
        dataSource.setUrl("jdbc:crypto:mysql://localhost:3306/test_db");
        dataSource.setUsername("root");
        dataSource.setPassword("password");
        
        return dataSource;
    }
    
    private void initializeCryptoConfig() {
        FieldCryptoConfig config = new FieldCryptoConfig();
        config.addEncryptField("users", "name");
        config.addEncryptField("users", "email");
        config.addDecryptField("users", "name");
        config.addDecryptField("users", "email");
        config.setAlgorithm("AES");
        config.setKey("MySecretKey12345");
        
        CryptoJdbcDriver.initialize(config, config.getKey());
    }
}
```

### 2. 使用JdbcTemplate

```java
@Service
public class UserService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    public void createUser(String name, String email, String phone) {
        String sql = "INSERT INTO users (name, email, phone) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, name, email, phone);
    }
    
    public List<Map<String, Object>> findUserByName(String name) {
        String sql = "SELECT id, name, email, phone FROM users WHERE name = ?";
        return jdbcTemplate.queryForList(sql, name);
    }
}
```

## 高级功能

### 1. 自定义加密算法

```java
// 实现自定义加密算法
public class CustomCryptoUtils {
    public static String encrypt(String plainText, String key) {
        // 自定义加密逻辑
        return customEncrypt(plainText, key);
    }
    
    public static String decrypt(String encryptedText, String key) {
        // 自定义解密逻辑
        return customDecrypt(encryptedText, key);
    }
}
```

### 2. 动态配置

```java
// 动态添加加密字段
FieldCryptoConfig config = CryptoJdbcDriver.getConfig();
config.addEncryptField("new_table", "new_field");
config.addDecryptField("new_table", "new_field");
```

### 3. 性能优化

```java
// 启用缓存
config.setCacheEnabled(true);
config.setCacheSize(1000);
config.setCacheTtl(3600);
```

## 测试

### 运行单元测试

```bash
mvn test
```

### 运行集成测试

```bash
mvn integration-test
```

### 性能测试

```bash
mvn test -Dtest=PerformanceTest
```

## 最佳实践

### 1. 密钥管理

- 使用环境变量或密钥管理服务存储加密密钥
- 定期轮换加密密钥
- 不要在代码中硬编码密钥

### 2. 性能优化

- 只对敏感字段进行加密
- 使用索引优化查询性能
- 考虑使用缓存减少重复计算

### 3. 安全考虑

- 使用强加密算法（AES-256）
- 保护加密密钥的安全
- 定期审计加密配置

## 故障排除

### 常见问题

1. **驱动未初始化**
   - 确保在创建连接前调用 `CryptoJdbcDriver.initialize()`

2. **加密解密失败**
   - 检查密钥是否正确
   - 验证字段配置是否正确

3. **性能问题**
   - 检查是否对过多字段进行加密
   - 考虑使用缓存优化

### 日志配置

```properties
# 启用调试日志
logging.level.io.github.hexlodev.core.crypto=DEBUG
```

## 贡献指南

1. Fork 项目
2. 创建特性分支
3. 提交更改
4. 推送到分支
5. 创建 Pull Request

## 许可证

本项目采用 Apache License 2.0 许可证。

## 联系方式

- 项目主页: https://github.com/hexlodev/securt-kit
- 问题反馈: https://github.com/hexlodev/securt-kit/issues
- 邮箱: hexlodev@example.com

## 更新日志

### v1.0.0 (2024-01-01)
- 初始版本发布
- 支持基本的字段加密解密功能
- 支持多种配置方式
- 提供完整的测试用例
