# Securt-Kit Core 使用指南

## 快速开始

### 1. 基本使用

```java
// 1. 创建加密配置
FieldCryptoConfig config = new FieldCryptoConfig();
config.addEncryptField("users", "name");
config.addEncryptField("users", "email");
config.addDecryptField("users", "name");
config.addDecryptField("users", "email");
config.setAlgorithm("AES");
config.setKey("MySecretKey12345");

// 2. 初始化加密驱动
CryptoJdbcDriver.initialize(config, config.getKey());

// 3. 使用加密驱动连接数据库
String url = "jdbc:crypto:mysql://localhost:3306/test_db";
Connection connection = DriverManager.getConnection(url, username, password);

// 4. 正常使用JDBC API，字段会自动加密解密
PreparedStatement stmt = connection.prepareStatement(
    "INSERT INTO users (name, email) VALUES (?, ?)");
stmt.setString(1, "张三");  // 自动加密
stmt.setString(2, "zhangsan@example.com");  // 自动加密
stmt.executeUpdate();

// 查询时自动解密
ResultSet rs = stmt.executeQuery("SELECT name, email FROM users");
while (rs.next()) {
    String name = rs.getString("name");  // 自动解密
    String email = rs.getString("email");  // 自动解密
}
```

### 2. 配置方式

#### 配置文件方式

创建 `crypto-config.properties`：

```properties
crypto.algorithm=AES
crypto.key=MySecretKey12345
crypto.encrypt.fields=users.name,users.email,orders.customer_name
crypto.decrypt.fields=users.name,users.email,orders.customer_name
```

#### 环境变量方式

```bash
export CRYPTO_ALGORITHM=AES
export CRYPTO_KEY=MySecretKey12345
export CRYPTO_ENCRYPT_FIELDS=users.name,users.email
export CRYPTO_DECRYPT_FIELDS=users.name,users.email
```

### 3. 支持的数据库

- MySQL
- PostgreSQL
- Oracle
- SQL Server
- H2
- SQLite
- 其他支持JDBC的数据库

### 4. 注意事项

1. **密钥管理**: 请妥善保管加密密钥，建议使用环境变量或密钥管理服务
2. **性能影响**: 加密解密会有一定的性能开销，建议只对敏感字段进行加密
3. **字段类型**: 目前主要支持字符串类型的字段加密
4. **SQL兼容性**: 支持标准的INSERT、UPDATE、SELECT、DELETE语句

### 5. 故障排除

#### 常见问题

1. **驱动未初始化**
   ```
   SQLException: CryptoJdbcDriver 未初始化
   ```
   解决：确保在创建连接前调用 `CryptoJdbcDriver.initialize()`

2. **加密解密失败**
   ```
   RuntimeException: 加密失败
   ```
   解决：检查密钥是否正确，字段配置是否正确

3. **连接失败**
   ```
   SQLException: 无法找到驱动
   ```
   解决：确保目标数据库的JDBC驱动已正确加载

#### 调试模式

启用调试日志：

```java
// 设置日志级别
System.setProperty("java.util.logging.level.io.github.hexlodev.core.crypto", "FINE");
```

### 6. 高级功能

#### 动态配置

```java
// 运行时添加新的加密字段
FieldCryptoConfig config = CryptoJdbcDriver.getConfig();
config.addEncryptField("new_table", "new_field");
config.addDecryptField("new_table", "new_field");
```

#### 自定义加密算法

```java
// 实现自定义加密逻辑
public class CustomCryptoUtils {
    public static String encrypt(String plainText, String key) {
        // 自定义加密实现
        return customEncrypt(plainText, key);
    }
    
    public static String decrypt(String encryptedText, String key) {
        // 自定义解密实现
        return customDecrypt(encryptedText, key);
    }
}
```

### 7. 性能优化建议

1. **字段选择**: 只对真正需要加密的敏感字段进行加密
2. **索引优化**: 避免对加密字段创建索引，考虑使用哈希值作为索引
3. **批量操作**: 使用批量操作减少数据库往返次数
4. **连接池**: 使用连接池提高性能

### 8. 安全建议

1. **密钥轮换**: 定期更换加密密钥
2. **访问控制**: 限制对加密字段的直接数据库访问
3. **审计日志**: 记录加密解密操作日志
4. **备份策略**: 确保加密密钥的安全备份

## 示例项目

参考 `src/main/java/io/github/hexlodev/core/crypto/example/` 目录下的示例代码：

- `CryptoExample.java`: 基本使用示例
- `ConfigurationExample.java`: 配置管理示例

## 技术支持

如有问题，请查看：
1. 项目README文档
2. 示例代码
3. 测试用例
4. 提交Issue到项目仓库
