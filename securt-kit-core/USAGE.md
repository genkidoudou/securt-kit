# Securt-Kit 使用文档

## 目录

1. [快速开始](#快速开始)
2. [配置说明](#配置说明)
3. [核心功能](#核心功能)
4. [API 文档](#api-文档)
5. [最佳实践](#最佳实践)
6. [常见问题](#常见问题)

## 快速开始

### 1. 添加依赖

在项目的 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>io.github.hexlodev.core</groupId>
    <artifactId>securt-kit-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置加密字段

创建配置文件 `crypto-config.properties` 或通过代码配置：

```java
@Configuration
public class CryptoConfig {
    
    @Bean
    public FieldEncryptorProperties fieldEncryptorProperties() {
        FieldEncryptorProperties props = new FieldEncryptorProperties();
        props.setEnable(true);
        
        // 配置需要加密的表和字段
        FieldEncryptorProperties.TableConfig tableConfig = new FieldEncryptorProperties.TableConfig();
        tableConfig.setTableName("user");
        
        FieldEncryptorProperties.FieldConfig nameField = new FieldEncryptorProperties.FieldConfig();
        nameField.setFieldName("name");
        nameField.setEncryptorStrategy(MyFieldEncryptorStrategy.class);
        
        FieldEncryptorProperties.FieldConfig phoneField = new FieldEncryptorProperties.FieldConfig();
        phoneField.setFieldName("phone");
        phoneField.setEncryptorStrategy(MyFieldEncryptorStrategy.class);
        
        tableConfig.setFields(Arrays.asList(nameField, phoneField));
        props.setTables(Arrays.asList(tableConfig));
        
        // 初始化缓存
        TableCache.init(props);
        
        return props;
    }
}
```

### 3. 实现加密策略

创建自定义加密策略类：

```java
@Component
public class MyFieldEncryptorStrategy implements FieldEncryptorStrategy {
    
    @Override
    public String encryption(String fieldValue) {
        // 实现加密逻辑
        // 例如：使用AES加密
        return encrypt(fieldValue);
    }
    
    @Override
    public String decryption(String fieldValue) {
        // 实现解密逻辑
        return decrypt(fieldValue);
    }
}
```

### 4. 配置数据源

在 Spring Boot 应用中配置数据源：

```java
@Configuration
public class DataSourceConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        // 使用拦截器驱动
        dataSource.setDriverClassName("io.github.hexlodev.core.interceptor.SimpleInterceptorDriver");
        dataSource.setJdbcUrl("jdbc:interceptor:mysql://localhost:3306/test_db");
        dataSource.setUsername("root");
        dataSource.setPassword("password");
        return dataSource;
    }
}
```

## 配置说明

### 字段加密配置

`FieldEncryptorProperties` 支持以下配置：

```java
public class FieldEncryptorProperties {
    private boolean enable = true;  // 是否启用加密
    
    private List<TableConfig> tables;  // 表配置列表
    
    public static class TableConfig {
        private String tableName;  // 表名
        private List<FieldConfig> fields;  // 字段配置列表
    }
    
    public static class FieldConfig {
        private String fieldName;  // 字段名
        private Class<? extends FieldEncryptorStrategy> encryptorStrategy;  // 加密策略类
    }
}
```

### 配置文件方式

创建 `application.yml`：

```yaml
securt-kit:
  enable: true
  tables:
    - table-name: user
      fields:
        - field-name: name
          encryptor-strategy: com.example.MyFieldEncryptorStrategy
        - field-name: phone
          encryptor-strategy: com.example.MyFieldEncryptorStrategy
    - table-name: order
      fields:
        - field-name: customer-name
          encryptor-strategy: com.example.MyFieldEncryptorStrategy
```

## 核心功能

### 1. 自动字段加密

在 INSERT 和 UPDATE 操作时，框架会自动识别需要加密的字段并加密：

```java
@Autowired
private UserMapper userMapper;

public void createUser(User user) {
    // name 和 phone 字段会自动加密
    userMapper.insert(user);
}

public void updateUser(User user) {
    // name 和 phone 字段会自动加密
    userMapper.updateById(user);
}
```

### 2. 自动字段解密

在 SELECT 查询时，框架会自动解密加密字段：

```java
public User getUserById(Long id) {
    // 查询结果中的 name 和 phone 字段会自动解密
    return userMapper.selectById(id);
}
```

### 3. SQL 解析与拦截

框架通过 JDBC 拦截器实现：
- 拦截 `PreparedStatement` 的参数设置
- 拦截 SQL 执行
- 解析 SQL 语句，识别表和字段
- 包装 `ResultSet` 实现自动解密

### 4. 支持的操作类型

- ✅ INSERT - 自动加密插入的字段值
- ✅ UPDATE - 自动加密更新的字段值  
- ✅ SELECT - 自动解密查询的字段值
- ✅ DELETE - 支持（但通常不需要加密）

## API 文档

### SecurtkitUtils

核心工具类，提供 SQL 解析功能：

```java
public class SecurtkitUtils {
    /**
     * 解析SQL语句，获取占位符与表字段的映射关系
     * 
     * @param sql SQL语句
     * @return Pair<占位符映射, 加密字段信息列表>
     */
    public static Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseSql(String sql)
            throws JSQLParserException;
    
    /**
     * 判断表是否需要加密处理
     * 
     * @param tables 表名集合
     * @return 是否需要加密
     */
    public static boolean needEncrypt(Collection<String> tables);
}
```

### TableCache

表配置缓存，管理加密配置：

```java
public class TableCache {
    /**
     * 初始化缓存
     * 
     * @param properties 加密配置属性
     */
    public static void init(FieldEncryptorProperties properties);
    
    /**
     * 获取需要加密的表集合
     * 
     * @return 表名集合
     */
    public static Set<String> getTables();
    
    /**
     * 获取表的字段加密策略
     * 
     * @param tableName 表名
     * @param fieldName 字段名
     * @return 加密策略类
     */
    public static Class<? extends FieldEncryptorStrategy> getTableFieldEncryptInfo(
            String tableName, String fieldName);
}
```

### FieldEncryptorStrategy

加密策略接口：

```java
public interface FieldEncryptorStrategy {
    /**
     * 加密字段值
     * 
     * @param fieldValue 原始字段值
     * @return 加密后的值
     */
    String encryption(String fieldValue);
    
    /**
     * 解密字段值
     * 
     * @param fieldValue 加密的字段值
     * @return 解密后的值
     */
    String decryption(String fieldValue);
}
```

## 最佳实践

### 1. 加密策略选择

- 对于敏感数据（如身份证号、手机号），使用强加密算法（AES-256）
- 对于需要模糊查询的字段，考虑使用可逆但不可读的加密方式
- 密钥管理：使用密钥管理服务（如 AWS KMS、HashiCorp Vault）存储密钥

### 2. 性能优化

- 只对必要的敏感字段进行加密
- 使用缓存减少重复的加密解密操作
- 对于大表，考虑分区或分表策略

### 3. 错误处理

```java
@Component
public class SafeFieldEncryptorStrategy implements FieldEncryptorStrategy {
    @Override
    public String encryption(String fieldValue) {
        try {
            return encrypt(fieldValue);
        } catch (Exception e) {
            logger.error("Encryption failed", e);
            // 返回原始值，避免业务中断
            return fieldValue;
        }
    }
}
```

### 4. 测试建议

- 单元测试：测试加密解密逻辑
- 集成测试：测试完整的 SQL 执行流程
- 性能测试：测试加密解密的性能影响

## 常见问题

### Q1: 为什么某些字段没有被加密？

**A:** 请检查：
1. 字段是否在配置中正确声明
2. 表名是否完全匹配（区分大小写）
3. SQL 语句中的表名是否与配置一致
4. 加密功能是否已启用（`enable=true`）

### Q2: 加密后的数据如何查询？

**A:** 框架会自动处理查询时的解密，但在 WHERE 条件中使用加密字段时需要注意：

```java
// ❌ 错误：直接使用明文值查询
userMapper.selectByPhone("13800138000");

// ✅ 正确：查询前先加密
String encryptedPhone = encryptorStrategy.encryption("13800138000");
userMapper.selectByPhone(encryptedPhone);
```

### Q3: 性能影响如何？

**A:** 
- 加密解密操作会增加一定的 CPU 开销
- 对于高并发场景，建议对加密操作进行性能测试
- 可以使用线程池或异步方式处理加密解密

### Q4: 支持批量操作吗？

**A:** 是的，框架支持批量 INSERT、UPDATE 操作，每个字段值都会自动加密。

### Q5: 如何迁移现有数据？

**A:** 建议的迁移步骤：

1. 添加新字段（如 `name_encrypted`）
2. 批量加密现有数据并更新到新字段
3. 切换到使用加密字段
4. 删除旧字段（可选）

## 日志配置

框架使用 Java Util Logging。可以通过日志配置控制日志级别：

```properties
# 启用详细日志（开发环境）
java.util.logging.level.io.github.hexlodev.core = FINE

# 只记录重要日志（生产环境）
java.util.logging.level.io.github.hexlodev.core = INFO

# 只记录错误日志
java.util.logging.level.io.github.hexlodev.core = WARNING
```

## 版本历史

### v1.0.0 (2025-11-02)
- ✅ 初始版本发布
- ✅ 支持 PreparedStatement 字段加密解密
- ✅ 支持 ResultSet 自动解密
- ✅ SQL 解析与表字段识别
- ✅ 支持自定义加密策略
- ✅ Spring Boot 集成支持

