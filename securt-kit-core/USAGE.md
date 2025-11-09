# Securt-Kit Core 使用文档

## 简介

本文档介绍如何在非 Spring Boot 环境中使用 `securt-kit-core` 模块。

## 添加依赖

```xml
<dependency>
    <groupId>io.github.hexlodev.core</groupId>
    <artifactId>securt-kit-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## 配置数据源

### 方式 1: 使用拦截驱动

```java
String url = "jdbc:interceptor:mysql://localhost:3306/testdb";
Connection conn = DriverManager.getConnection(url, username, password);
```

### 方式 2: 在数据源配置中使用

```java
HikariConfig config = new HikariConfig();
config.setDriverClassName("io.github.hexlodev.core.interceptor.SimpleInterceptorDriver");
config.setJdbcUrl("jdbc:interceptor:mysql://localhost:3306/testdb");
HikariDataSource dataSource = new HikariDataSource(config);
```

## 配置加密字段

### 方式 1: 使用配置文件

创建 `application.yml` 或 `application.properties`，然后通过代码加载：

```java
@Configuration
public class CryptoConfig {
    
    @Bean
    public FieldEncryptorProperties fieldEncryptorProperties() {
        FieldEncryptorProperties props = new FieldEncryptorProperties();
        props.setEnable(true);
        props.setFailurePolicy(FailurePolicy.FALLBACK);
        
        // 配置表
        FieldEncryptorProperties.TableConfig tableConfig = new FieldEncryptorProperties.TableConfig();
        tableConfig.setTableName("user");
        
        FieldEncryptorProperties.FieldConfig nameField = new FieldEncryptorProperties.FieldConfig();
        nameField.setFieldName("name");
        
        FieldEncryptorProperties.FieldConfig phoneField = new FieldEncryptorProperties.FieldConfig();
        phoneField.setFieldName("phone");
        
        tableConfig.setFields(Arrays.asList(nameField, phoneField));
        props.setTables(Arrays.asList(tableConfig));
        
        // 初始化缓存
        TableCache.init(props);
        
        return props;
    }
}
```

### 方式 2: 纯代码配置

```java
public class CryptoInitializer {
    
    public static void init() {
        FieldEncryptorProperties props = new FieldEncryptorProperties();
        props.setEnable(true);
        
        // 配置表...
        TableCache.init(props);
    }
}
```

## 实现加密策略

```java
public class MyFieldEncryptorStrategy implements FieldEncryptorStrategy {
    
    @Override
    public String encryption(String oldValue) {
        if (oldValue == null) {
            return null;
        }
        // 实现加密逻辑
        return encrypt(oldValue);
    }
    
    @Override
    public String decryption(String oldValue) {
        if (oldValue == null) {
            return null;
        }
        // 实现解密逻辑
        return decrypt(oldValue);
    }
}
```

## 使用示例

```java
// 配置完成后，正常使用 JDBC 即可
Connection conn = DriverManager.getConnection(
    "jdbc:interceptor:mysql://localhost:3306/testdb", 
    username, 
    password
);

// INSERT 操作 - 自动加密
PreparedStatement stmt = conn.prepareStatement("INSERT INTO user (name, phone) VALUES (?, ?)");
stmt.setString(1, "张三");  // 会自动加密
stmt.setString(2, "13800138000");  // 会自动加密
stmt.executeUpdate();

// SELECT 操作 - 自动解密
PreparedStatement query = conn.prepareStatement("SELECT * FROM user WHERE id = ?");
query.setLong(1, 1L);
ResultSet rs = query.executeQuery();
while (rs.next()) {
    String name = rs.getString("name");  // 会自动解密
    String phone = rs.getString("phone");  // 会自动解密
}
```

## API 参考

### TableCache

表配置缓存管理：

```java
// 初始化
TableCache.init(properties);

// 获取表的加密字段信息
Map<String, Class<? extends FieldEncryptorStrategy>> fieldMap = 
    TableCache.getTableFieldEncryptInfo(tableName, datasourceId);
```

### SecurtkitUtils

SQL 解析工具：

```java
// 解析 SQL
Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = 
    SecurtkitUtils.parseSql(sql);

// 判断表是否需要加密
boolean needEncrypt = SecurtkitUtils.needEncrypt(tableNames);
```

## 注意事项

1. **必须使用拦截驱动**：URL 必须使用 `jdbc:interceptor:` 前缀
2. **配置初始化**：在使用前必须调用 `TableCache.init()`
3. **线程安全**：`TableCache` 是线程安全的，可以在多线程环境中使用

## 相关文档

- [Core README](README.md)
- [主 README](../README.md)
- [快速开始](../docs/QUICK-START.md)

