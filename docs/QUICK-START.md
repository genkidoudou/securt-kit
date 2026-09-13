# 快速开始指南

本指南将帮助你在 5 分钟内快速集成 Securt-Kit 到你的项目中。

## 前置要求

- Java 8+（Boot 2.7）或 Java 17+（Boot 3.x）
- Spring Boot 2.7.x 或 3.x
- Maven 或 Gradle

## 步骤 1: 添加依赖

### Spring Boot 2.7.x 项目

**Maven**:
```xml
<dependency>
    <groupId>io.github.genkidoudou</groupId>
    <artifactId>securt-kit-starter-boot2</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**Gradle**:
```groovy
implementation 'io.github.genkidoudou:securt-kit-starter-boot2:1.0-SNAPSHOT'
```

### Spring Boot 3.x 项目

**Maven**:
```xml
<dependency>
    <groupId>io.github.genkidoudou</groupId>
    <artifactId>securt-kit-starter-boot3</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**Gradle**:
```groovy
implementation 'io.github.genkidoudou:securt-kit-starter-boot3:1.0-SNAPSHOT'
```

## 步骤 2: 选择通道模式并配置数据源

`securtkit.encryptor.mode` 可选：`JDBC`（默认）| `MYBATIS` | `OFF`。  
**同一数据源禁止 JDBC 与 MYBATIS 同时生效。**

### 模式 A：JDBC（默认）

修改数据源驱动与 URL：

```yaml
spring:
  datasource:
    driver-class-name: io.github.genkidoudou.core.interceptor.SimpleInterceptorDriver
    url: jdbc:interceptor:mysql://localhost:3306/testdb
    username: root
    password: password

securtkit:
  encryptor:
    enable: true
    mode: JDBC
```

**重要**：JDBC 模式下 URL 必须使用 `jdbc:interceptor:` 前缀。

### 模式 B：MyBatis（无需改驱动）

适用于以 MyBatis / MyBatis-Plus 为主、不想改连接池驱动的场景：

```yaml
spring:
  datasource:
    # 保持原驱动与 URL 即可，不要使用 jdbc:interceptor:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/testdb
    username: root
    password: password

securtkit:
  encryptor:
    enable: true
    mode: MYBATIS
```

Starter 会自动注册 `EncryptInterceptor`（需 classpath 有 MyBatis；starter 已传递 `securt-kit-mybatis`）。

详见 [MyBatis / JDBC 双模式设计](MYBATIS-MODE-DESIGN.md)。

## 步骤 3: 配置加密字段

在 `application.yml` 中添加加密配置：

```yaml
securtkit:
  encryptor:
    enable: true
    mode: JDBC   # 或 MYBATIS
    failure-policy: FALLBACK
    tables:
      - table-name: user
        fields:
          - field-name: name
          - field-name: phone
          - field-name: email
      - table-name: order
        fields:
          - field-name: customer_name
          - field-name: customer_phone
```

## 步骤 4: 实现加密策略（可选）

如果不指定策略，框架会使用默认策略。要实现自定义策略：

```java
@Component
public class MyFieldEncryptorStrategy implements FieldEncryptorStrategy {
    
    @Override
    public String encryption(String plainText) {
        if (plainText == null) {
            return null;
        }
        // 实现你的加密逻辑
        return AESUtil.encrypt(plainText);
    }
    
    @Override
    public String decryption(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        // 实现你的解密逻辑
        return AESUtil.decrypt(cipherText);
    }
}
```

然后在配置中指定：

```yaml
securtkit:
  encryptor:
    tables:
      - table-name: user
        fields:
          - field-name: phone
            strategy: com.example.MyFieldEncryptorStrategy
```

## 步骤 5: 使用

框架会自动处理加密解密，业务代码无需修改：

```java
@Service
public class UserService {
    
    @Autowired
    private UserMapper userMapper;
    
    // 插入时自动加密
    public void createUser(User user) {
        userMapper.insert(user);  // name 和 phone 字段会自动加密
    }
    
    // 查询时自动解密
    public User getUserById(Long id) {
        return userMapper.selectById(id);  // name 和 phone 字段会自动解密
    }
    
    // 更新时自动加密
    public void updateUser(User user) {
        userMapper.updateById(user);  // name 和 phone 字段会自动加密
    }
}
```

## 步骤 6: 验证

### 方式 1: 使用监控界面

启动应用后，访问 `http://localhost:8080/monitor/index.html`，使用监控界面测试加密解密功能。

### 方式 2: 查看数据库

直接查看数据库，确认字段值已被加密存储。

### 方式 3: 查看日志

框架会输出详细的日志信息，包括加密解密操作。

## 完整配置示例

```yaml
spring:
  datasource:
    driver-class-name: io.github.genkidoudou.core.interceptor.SimpleInterceptorDriver
    url: jdbc:interceptor:mysql://localhost:3306/testdb
    username: root
    password: password

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
          - field-name: email
      - table-name: order
        fields:
          - field-name: customer_name
          - field-name: customer_phone
  monitor:
    enabled: true
    username: admin
    password: admin
```

## 下一步

- 查看 [使用指南](USAGE.md) 了解更多配置选项
- 查看 [多数据源配置](MULTI-DATASOURCE.md) 了解多数据源场景
- 查看 [API 文档](API.md) 了解详细 API

## 常见问题

**Q: 为什么必须使用 `jdbc:interceptor:` 前缀？**

A: 这是框架识别需要拦截的数据库连接的方式。框架会提取真实 URL 并转发给底层驱动。

**Q: 如何知道加密是否生效？**

A: 可以通过监控界面测试，或直接查看数据库中的字段值（应该是加密后的值）。

**Q: 支持哪些数据库？**

A: 支持所有基于 JDBC 的数据库（MySQL、PostgreSQL、Oracle、H2 等）。

