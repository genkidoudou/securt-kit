# 多数据源测试使用说明

## 概述

本文档说明如何使用多数据源测试配置来验证多数据源场景下的字段加密功能。

支持两种方式：
1. **手动配置数据源**：使用 `MultiDataSourceConfig`（适用于纯 JDBC 测试）
2. **MyBatis-Plus 多数据源**：使用 `dynamic-datasource`（适用于 MyBatis-Plus 项目，推荐）

## 配置文件

### application-multi-datasource.yml

多数据源测试专用的配置文件，位于 `src/test/resources/application-multi-datasource.yml`。

**使用 MyBatis-Plus 的 dynamic-datasource 配置格式**：

配置说明：

#### 1. Spring 数据源配置（使用 dynamic-datasource）

```yaml
spring:
  datasource:
    dynamic:
      primary: primary  # 主数据源
      datasource:
        primary:
          driver-class-name: io.github.hexlodev.core.interceptor.SimpleInterceptorDriver
          url: 'jdbc:interceptor:h2:mem:primary_db?datasource-id=primary'
          username: sa
          password:
        secondary:
          driver-class-name: io.github.hexlodev.core.interceptor.SimpleInterceptorDriver
          url: 'jdbc:interceptor:h2:mem:secondary_db?datasource-id=secondary'
          username: sa
          password:
        third:
          driver-class-name: io.github.hexlodev.core.interceptor.SimpleInterceptorDriver
          url: 'jdbc:interceptor:h2:mem:third_db?datasource-id=third'
          username: sa
          password:
```

#### 2. 加密配置（新配置方式：在 tables 中直接指定 datasource-id）

```yaml
securtkit:
  encryptor:
    enable: true
    failure-policy: FALLBACK
    sql-parse-cache:
      enable: true
      max-size: 1000
    tables:
      # 主数据源：加密 name 和 phone
      - table-name: user
        datasource-id: primary  # 指定数据源标识
        fields:
          - field-name: name
          - field-name: phone
      
      # 从数据源：只加密 name（不加密 phone）
      - table-name: user
        datasource-id: secondary  # 指定数据源标识
        fields:
          - field-name: name
      
      # 第三方数据源不配置（等于关闭加密）
      # 或者可以为所有数据源配置（不指定 datasource-id）
      - table-name: order
        # 不指定 datasource-id，应用到所有数据源
        fields:
          - field-name: amount
```

**说明**：
- `datasource-id` 字段是可选的
- 如果指定了 `datasource-id`，则该表配置只应用到该数据源
- 如果不指定 `datasource-id`，则该表配置应用到所有数据源（单数据源场景的默认行为）

## 测试类

### 1. MultiDataSourceTest（纯 JDBC 测试）

多数据源测试类，位于 `src/test/java/io/github/test/MultiDataSourceTest.java`。

使用手动配置的数据源，适用于纯 JDBC 场景。

### 2. MultiDataSourceMyBatisPlusTest（MyBatis-Plus 测试，推荐）

MyBatis-Plus 多数据源测试类，位于 `src/test/java/io/github/test/MultiDataSourceMyBatisPlusTest.java`。

使用 `dynamic-datasource` 实现多数据源，支持 `@DS` 注解切换数据源。

测试场景：

1. **主数据源加密测试** (`testPrimaryDataSourceEncryption`)
   - 验证主数据源加密 `name` 和 `phone` 字段
   - 验证插入时自动加密，查询时自动解密

2. **从数据源加密测试** (`testSecondaryDataSourceEncryption`)
   - 验证从数据源只加密 `name` 字段
   - 验证 `phone` 字段不加密（保持明文）

3. **第三方数据源测试** (`testThirdDataSourceNoEncryption`)
   - 验证第三方数据源关闭加密功能
   - 所有字段都保持明文存储

4. **配置隔离测试** (`testConfigurationIsolation`)
   - 验证不同数据源的配置相互独立
   - 验证字段级别的配置隔离

5. **数据源标识提取测试** (`testDatasourceIdExtraction`)
   - 验证从连接属性中正确提取数据源标识

## 配置类

### 1. MultiDataSourceConfig（手动配置）

多数据源配置类，位于 `src/test/java/io/github/test/config/MultiDataSourceConfig.java`。

手动配置三个数据源 Bean，适用于纯 JDBC 场景。

### 2. MultiDataSourceMyBatisPlusConfig（推荐）

MyBatis-Plus 多数据源配置类，位于 `src/test/java/io/github/test/config/MultiDataSourceMyBatisPlusConfig.java`。

使用 `dynamic-datasource` 自动配置，数据源配置在 `application-multi-datasource.yml` 中。

### 3. MultiDataSourceUserService（Service 层示例）

多数据源用户服务类，位于 `src/test/java/io/github/test/service/MultiDataSourceUserService.java`。

演示如何在 Service 层使用 `@DS` 注解切换数据源：

```java
@Service
public class MultiDataSourceUserService {
    
    @DS("primary")  // 使用主数据源
    public int saveToPrimary(UserEntity user) {
        return userEntityMapper.insert(user);
    }
    
    @DS("secondary")  // 使用从数据源
    public UserEntity getFromSecondary(Long id) {
        return userEntityMapper.selectById(id);
    }
}
```

## 运行测试

### 方式1：运行 MyBatis-Plus 多数据源测试（推荐）

```bash
# 运行 MyBatis-Plus 多数据源测试
mvn test -Dtest=MultiDataSourceMyBatisPlusTest
```

### 方式2：运行纯 JDBC 多数据源测试

```bash
# 运行纯 JDBC 多数据源测试
mvn test -Dtest=MultiDataSourceTest
```

### 方式3：在 IDE 中运行

1. 打开 `MultiDataSourceMyBatisPlusTest.java` 或 `MultiDataSourceTest.java`
2. 右键点击类名或测试方法
3. 选择 "Run" 运行测试

### 方式4：运行所有测试

```bash
mvn test
```

## 测试验证点

### 1. 配置隔离验证

- ✅ 主数据源配置不影响从数据源
- ✅ 从数据源配置不影响第三方数据源
- ✅ 全局配置作为默认值，数据源配置可以覆盖

### 2. 加密功能验证

- ✅ 主数据源：`name` 和 `phone` 字段都加密
- ✅ 从数据源：只有 `name` 字段加密，`phone` 字段明文
- ✅ 第三方数据源：所有字段都不加密

### 3. 数据源标识验证

- ✅ 连接属性 `datasource-id` 正确提取
- ✅ 连接创建时正确绑定数据源标识
- ✅ SQL 执行时使用正确的数据源配置

## 注意事项

1. **数据源标识传递方式（推荐）**：通过连接属性传递 `datasource-id`，而不是在 URL 中配置
   ```yaml
   hikari:
     data-source-properties:
       datasource-id: primary
   ```
   或代码方式：
   ```java
   dataSource.addDataSourceProperty("datasource-id", "primary");
   ```

2. **URL 格式**：数据源 URL **不需要**包含 `datasource-id` 参数，保持 URL 干净
   ```yaml
   url: 'jdbc:interceptor:h2:mem:primary_db;DB_CLOSE_DELAY=-1;MODE=MySQL'
   ```

3. **配置顺序**：数据源配置会覆盖全局配置（按表名合并）

4. **默认数据源**：如果未指定 `datasource-id`，则使用 `"default"` 作为标识

5. **H2 数据库**：测试使用 H2 内存数据库，每个数据源使用不同的数据库名（`primary_db`、`secondary_db`、`third_db`）

## 示例配置

### 完整配置示例（新配置方式）

```yaml
securtkit:
  encryptor:
    enable: true
    failure-policy: FALLBACK
    sql-parse-cache:
      enable: true
      max-size: 1000
    tables:
      # 主数据源配置
      - table-name: user
        datasource-id: primary
        fields:
          - field-name: name
          - field-name: phone
          - field-name: email
      
      # 从数据源配置
      - table-name: user
        datasource-id: secondary
        fields:
          - field-name: name
      
      # 所有数据源共享的配置（不指定 datasource-id）
      - table-name: order
        fields:
          - field-name: amount
      
      # 只读库不配置（等于关闭加密）
```

## 使用 MyBatis-Plus 多数据源

### 1. 添加依赖

在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>dynamic-datasource-spring-boot-starter</artifactId>
    <version>3.6.1</version>
</dependency>
```

### 2. 配置数据源

在 `application-multi-datasource.yml` 中配置：

```yaml
spring:
  datasource:
    dynamic:
      primary: primary
      datasource:
        primary:
          driver-class-name: io.github.hexlodev.core.interceptor.SimpleInterceptorDriver
          url: 'jdbc:interceptor:h2:mem:primary_db?datasource-id=primary'
        secondary:
          driver-class-name: io.github.hexlodev.core.interceptor.SimpleInterceptorDriver
          url: 'jdbc:interceptor:h2:mem:secondary_db?datasource-id=secondary'
```

### 3. 使用 @DS 注解切换数据源

```java
@Service
public class UserService {
    
    @Autowired
    private UserEntityMapper userEntityMapper;
    
    @DS("primary")  // 使用主数据源
    public void saveToPrimary(UserEntity user) {
        userEntityMapper.insert(user);
    }
    
    @DS("secondary")  // 使用从数据源
    public List<UserEntity> queryFromSecondary() {
        return userEntityMapper.selectList(null);
    }
}
```

### 4. 数据源标识传递

**推荐方式**：通过 URL 参数传递 `datasource-id`（通用方式，适用于所有连接池，如 HikariCP、Druid 等）：

```yaml
primary:
  driver-class-name: io.github.hexlodev.core.interceptor.SimpleInterceptorDriver
  # 通过 URL 参数传递数据源标识（通用方式，适用于所有连接池）
  # 注意：H2 数据库使用 ; 作为参数分隔符，MySQL 等使用 ? 或 &
  url: 'jdbc:interceptor:h2:mem:primary_db;DB_CLOSE_DELAY=-1;MODE=MySQL;datasource-id=primary'
  hikari:
    maximum-pool-size: 5
```

**说明**：
- **为什么使用 URL 参数**：`connection-properties` 和 `hikari.data-source-properties` 中的属性不会被传递到 JDBC 驱动的 `connect(String url, Properties info)` 方法的 `info` 参数中，`info` 通常只包含 `user` 和 `password`
- **URL 参数格式**：
  - H2 数据库：使用 `;` 作为参数分隔符，如 `jdbc:interceptor:h2:mem:db;datasource-id=primary`
  - MySQL 等：使用 `?` 或 `&` 作为参数分隔符，如 `jdbc:interceptor:mysql://localhost:3306/db?datasource-id=primary`
- 拦截器驱动会自动从 URL 中提取 `datasource-id`，并在传递给底层驱动前移除该参数，确保底层驱动不会收到未知参数
- `dynamic-datasource` 会根据 `@DS` 注解的值（如 `"primary"`）选择对应的数据源，然后我们的拦截器驱动会从 URL 中提取 `datasource-id`，用于查找对应的加密配置

## 常见问题

### Q1: 如何验证数据确实被加密了？

A: 在测试中，我们直接查询数据库（不使用拦截器）来验证：
```java
// 直接查询数据库（绕过拦截器）
try (Connection conn = dataSource.getConnection();
     Statement stmt = conn.createStatement();
     ResultSet rs = stmt.executeQuery("SELECT name, phone FROM user")) {
    // 如果数据是加密的，这里的值应该不等于原始值
}
```

### Q2: 如何配置不同的加密策略？

A: 在字段配置中指定 `strategy` 属性：
```yaml
fields:
  - field-name: phone
    strategy: com.example.CustomEncryptorStrategy
```

### Q3: @DS 注解如何与数据源标识关联？

A: `@DS("primary")` 注解的值必须与配置中的数据源名称和 URL 中的 `datasource-id` 参数值一致：

```yaml
# 配置中的数据源名称
datasource:
  primary:  # ← 这里
    url: 'jdbc:interceptor:h2:mem:db;datasource-id=primary'  # ← URL 中的参数值
```

```java
@DS("primary")  // ← 这里的值必须与上面两个一致
public void method() {
    // ...
}
```

**注意**：使用 URL 参数方式而不是 `connection-properties` 或 `hikari.data-source-properties`，因为后两者不会被传递到 JDBC 驱动的连接方法中。

### Q4: dynamic-datasource 会自动创建数据源吗？

A: 是的，`dynamic-datasource` 会根据 `spring.datasource.dynamic.datasource` 配置自动创建数据源。我们只需要确保每个数据源的连接属性中包含正确的 `datasource-id` 即可。

## 相关文档

- [多数据源设计方案](../MULTI-DATASOURCE-DESIGN.md)
- [使用文档](../securt-kit-core/USAGE.md)

