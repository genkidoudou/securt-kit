# Securt-Kit Core 模块

## 简介

`securt-kit-core` 是 Securt-Kit 的核心模块，提供数据库字段加密解密的基础功能。该模块版本无关，支持 Java 8+，不依赖任何 Spring Boot 版本。

## 核心组件

### 1. 拦截器层（interceptor）

负责拦截 JDBC 操作，实现透明加密解密：

- **SimpleInterceptorDriver**: JDBC 驱动拦截器，拦截数据库连接请求
- **SimpleInterceptorConnection**: 连接包装器，包装真实的数据库连接
- **SimpleInterceptorPreparedStatement**: 预编译语句包装器，处理参数加密
- **SimpleInterceptorStatement**: 普通语句包装器
- **SimpleInterceptorCallableStatement**: 存储过程语句包装器
- **ResultSetDecryptingProxy**: 结果集解密代理，处理查询结果解密

### 2. 解析器层（parser）

负责 SQL 解析和字段识别：

- **SecurtkitUtils**: SQL 解析工具类，提供核心解析方法
- **PoJoEncrtptorStatementVisitor**: SQL 访问者，提取表名和字段信息
- **PlaceholderSelectVisitor**: 占位符选择访问者，处理 SELECT 语句
- **SqlParseCache**: SQL 解析结果缓存，提升性能

### 3. 配置层（config）

负责配置管理和初始化：

- **FieldEncryptorProperties**: 字段加密配置属性类
- **ConfigInitializer**: 配置初始化器，初始化表配置缓存
- **TableCache**: 表配置缓存，管理加密配置
- **DataSourceConfigManager**: 数据源配置管理器（多数据源场景）

### 4. 策略层（strategy）

负责加密策略接口定义：

- **FieldEncryptorStrategy**: 加密策略接口，定义加密和解密方法

### 5. 异常处理（exception）

提供统一的异常处理机制：

- **SecurtKitException**: 基础异常类
- **ConfigurationException**: 配置异常
- **EncryptionException**: 加密异常
- **DecryptionException**: 解密异常
- **SqlParseException**: SQL 解析异常
- **EncryptionHandler**: 异常处理器，支持多种失败策略

## 使用方式

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.hexlodev.core</groupId>
    <artifactId>securt-kit-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置数据源

修改数据源驱动为拦截驱动：

```yaml
spring:
  datasource:
    driver-class-name: io.github.hexlodev.core.interceptor.SimpleInterceptorDriver
    url: jdbc:interceptor:mysql://localhost:3306/testdb
```

### 3. 配置加密字段

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

### 4. 初始化配置（非 Spring Boot 环境）

```java
FieldEncryptorProperties properties = new FieldEncryptorProperties();
// 设置配置...
TableCache.init(properties);
```

## API 文档

### SecurtkitUtils

核心工具类，提供 SQL 解析功能：

```java
public class SecurtkitUtils {
    /**
     * 解析 SQL 语句，获取占位符与表字段的映射关系
     */
    public static Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseSql(String sql)
            throws JSQLParserException;
    
    /**
     * 判断表是否需要加密处理
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
     */
    public static void init(FieldEncryptorProperties properties);
    
    /**
     * 获取表的加密字段信息
     */
    public static Map<String, Class<? extends FieldEncryptorStrategy>> getTableFieldEncryptInfo(
            String tableName, String datasourceId);
}
```

### FieldEncryptorStrategy

加密策略接口：

```java
public interface FieldEncryptorStrategy {
    /**
     * 加密方法
     */
    String encryption(String oldValue);
    
    /**
     * 解密方法
     */
    String decryption(String oldValue);
}
```

## 技术特性

- ✅ 版本无关：支持 Java 8+，不依赖 Spring Boot 版本
- ✅ 高性能：SQL 解析缓存、优化的字符串操作
- ✅ 线程安全：所有组件都经过线程安全设计
- ✅ 异常处理：完善的异常处理机制，支持多种失败策略
- ✅ 多数据源：支持多数据源场景下的差异化配置

## 注意事项

1. **必须使用拦截驱动**：数据源 URL 必须使用 `jdbc:interceptor:` 前缀
2. **配置初始化**：在 Spring Boot 环境中会自动初始化，非 Spring Boot 环境需要手动调用 `TableCache.init()`
3. **加密策略**：必须实现 `FieldEncryptorStrategy` 接口，确保加密解密互逆
