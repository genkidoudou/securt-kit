# JDBC Interceptor

基于P6Spy实现的JDBC拦截框架，提供透明的JDBC调用拦截功能。

## 功能特性

- **透明拦截**: 通过URL前缀 `jdbc:interceptor:` 拦截JDBC调用
- **完整包装**: 包装所有JDBC对象（Connection、Statement、PreparedStatement、CallableStatement、ResultSet）
- **日志记录**: 记录SQL执行时间、参数设置、数据获取等操作
- **无侵入性**: 不需要修改现有代码，只需修改数据库连接URL

## 核心组件

### 1. InterceptorDriver
- 自定义JDBC驱动，拦截 `jdbc:interceptor:` 前缀的URL
- 自动注册到DriverManager
- 提取真实URL并查找底层JDBC驱动

### 2. InterceptorConnectionWrapper
- 包装Connection对象
- 拦截Statement、PreparedStatement、CallableStatement的创建
- 记录连接建立和关闭

### 3. InterceptorStatementWrapper
- 包装Statement对象
- 拦截SQL执行方法（executeQuery、executeUpdate、execute）
- 记录执行时间和结果

### 4. InterceptorPreparedStatementWrapper
- 包装PreparedStatement对象
- 拦截参数设置和数据获取
- 记录SQL和参数信息

### 5. InterceptorCallableStatementWrapper
- 包装CallableStatement对象
- 拦截存储过程调用
- 记录参数注册和结果获取

### 6. InterceptorResultSetWrapper
- 包装ResultSet对象
- 拦截数据获取和游标移动
- 记录数据访问模式

## 使用方法

### 1. 基本使用

```java
// 原始URL
String originalUrl = "jdbc:h2:mem:testdb";

// 拦截URL
String interceptorUrl = "jdbc:interceptor:h2:mem:testdb";

// 建立连接
Connection conn = DriverManager.getConnection(interceptorUrl, username, password);
```

### 2. Spring Boot配置

```properties
# application.properties
spring.datasource.url=jdbc:interceptor:h2:mem:testdb
spring.datasource.driver-class-name=io.github.hexlodev.core.interceptor.InterceptorDriver
```

### 3. 数据源配置

```java
@Configuration
public class DataSourceConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:interceptor:h2:mem:testdb");
        dataSource.setDriverClassName("io.github.hexlodev.core.interceptor.InterceptorDriver");
        return dataSource;
    }
}
```

## 拦截功能

### SQL执行拦截
- 记录SQL语句
- 记录执行时间
- 记录影响行数
- 记录执行结果

### 参数拦截
- 记录PreparedStatement参数设置
- 记录CallableStatement参数注册
- 记录参数值（敏感信息可配置过滤）

### 数据访问拦截
- 记录ResultSet数据获取
- 记录游标移动操作
- 记录数据更新操作

## 日志配置

### 日志级别
```properties
# 详细日志
logging.level.io.github.hexlodev.core.interceptor=DEBUG

# 一般日志
logging.level.io.github.hexlodev.core.interceptor=INFO

# 警告日志
logging.level.io.github.hexlodev.core.interceptor=WARN
```

### 日志输出示例
```
INFO: Executing prepared query: SELECT * FROM users WHERE id = ?
INFO: Setting string parameter 1 = john@example.com
INFO: Prepared query executed in 15ms
INFO: Getting string from column 1 = john@example.com
INFO: Moving to next row
INFO: ResultSet closed
```

## 扩展功能

### 1. 自定义拦截器
```java
public class CustomInterceptorDriver extends InterceptorDriver {
    // 重写拦截逻辑
}
```

### 2. 性能监控
- SQL执行时间统计
- 慢查询检测
- 连接池监控

### 3. 安全审计
- SQL注入检测
- 敏感数据访问记录
- 权限变更跟踪

## 与P6Spy的区别

| 特性 | JDBC Interceptor | P6Spy |
|------|------------------|-------|
| 功能范围 | 基础拦截 | 完整监控套件 |
| 配置复杂度 | 简单 | 复杂 |
| 性能开销 | 低 | 中等 |
| 扩展性 | 高 | 中等 |
| 学习成本 | 低 | 高 |

## 注意事项

1. **性能影响**: 拦截会增加少量性能开销，生产环境建议调整日志级别
2. **敏感信息**: 默认记录所有参数值，注意保护敏感数据
3. **驱动兼容**: 需要底层JDBC驱动支持
4. **版本兼容**: 基于JDBC 4.0规范实现

## 示例代码

参考 `SimpleInterceptorDemo.java` 了解基本用法。

## 许可证

Apache License 2.0
