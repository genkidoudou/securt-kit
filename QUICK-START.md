# Securt-Kit 快速开始指南

## 📋 目录

- [5分钟快速体验](#5分钟快速体验)
- [Spring Boot 集成](#spring-boot-集成)
- [MyBatis 集成](#mybatis-集成)
- [多表查询示例](#多表查询示例)
- [常见问题](#常见问题)

---

## ⚡ 5分钟快速体验

### 步骤1: 添加依赖

```xml
<dependency>
    <groupId>io.github.hexlodev</groupId>
    <artifactId>securt-kit-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 步骤2: 配置加密字段

创建 `application.yml`:

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

### 步骤3: 实现加密策略

```java
@Component
public class MyEncryptorStrategy implements FieldEncryptorStrategy {
    
    @Override
    public String encryption(String value) {
        // 简单示例：Base64 编码（实际应使用加密算法）
        return Base64.getEncoder().encodeToString(value.getBytes());
    }
    
    @Override
    public String decryption(String value) {
        return new String(Base64.getDecoder().decode(value));
    }
}
```

### 步骤4: 配置数据源

```yaml
spring:
  datasource:
    driver-class-name: io.github.hexlodev.core.interceptor.SimpleInterceptorDriver
    url: jdbc:interceptor:h2:mem:testdb
    username: sa
    password:
```

### 步骤5: 使用（无需修改业务代码）

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
}

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;
    
    public void createUser() {
        User user = new User();
        user.setName("张三");      // 自动加密
        user.setPhone("13800138000"); // 自动加密
        userMapper.insert(user);
    }
    
    public User getUser(Long id) {
        User user = userMapper.selectById(id);
        System.out.println(user.getName());  // 自动解密
        return user;
    }
}
```

**完成！** 就这么简单，框架会自动处理加密解密。

---

## 🌱 Spring Boot 集成

### 完整示例

#### 1. pom.xml

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>2.6.13</version>
</parent>

<dependencies>
    <!-- Securt-Kit Starter -->
    <dependency>
        <groupId>io.github.hexlodev</groupId>
        <artifactId>securt-kit-starter</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- MyBatis-Plus -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-boot-starter</artifactId>
        <version>3.5.8</version>
    </dependency>
    
    <!-- 数据库驱动 -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
    </dependency>
</dependencies>
```

#### 2. application.yml

```yaml
spring:
  datasource:
    driver-class-name: io.github.hexlodev.core.interceptor.SimpleInterceptorDriver
    url: jdbc:interceptor:mysql://localhost:3306/test_db
    username: root
    password: password

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

logging:
  level:
    io.github.hexlodev.core: DEBUG
```

#### 3. 实体类

```java
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String name;    // 加密字段
    private String phone;   // 加密字段
    private Integer age;
    
    // getter/setter...
}
```

#### 4. Mapper

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

#### 5. Service

```java
@Service
public class UserService {
    
    @Autowired
    private UserMapper userMapper;
    
    public User createUser(String name, String phone) {
        User user = new User();
        user.setName(name);    // 自动加密
        user.setPhone(phone);  // 自动加密
        user.setAge(25);
        userMapper.insert(user);
        return user;
    }
    
    public User getUser(Long id) {
        // 查询时自动解密
        return userMapper.selectById(id);
    }
    
    public List<User> getAllUsers() {
        // 查询列表时自动解密
        return userMapper.selectList(null);
    }
}
```

---

## 📝 MyBatis 集成

### 方式1: MyBatis-Plus（推荐）

MyBatis-Plus 提供了丰富的 CRUD 方法，无需编写 SQL：

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 基础方法已自动支持加密解密
    // - insert()
    // - selectById()
    // - selectList()
    // - updateById()
    // - deleteById()
}
```

### 方式2: 原生 MyBatis

使用 `@Select`、`@Insert` 等注解：

```java
@Mapper
public interface UserMapper {
    
    @Insert("INSERT INTO user (name, phone, age) VALUES (#{name}, #{phone}, #{age})")
    int insert(User user);  // name 和 phone 自动加密
    
    @Select("SELECT * FROM user WHERE id = #{id}")
    User selectById(Long id);  // 结果自动解密
    
    @Update("UPDATE user SET name = #{name}, phone = #{phone} WHERE id = #{id}")
    int update(User user);  // name 和 phone 自动加密
}
```

### 方式3: XML Mapper

创建 `UserMapper.xml`:

```xml
<mapper namespace="com.example.mapper.UserMapper">
    
    <insert id="insert" parameterType="com.example.entity.User">
        INSERT INTO user (name, phone, age) 
        VALUES (#{name}, #{phone}, #{age})
        <!-- name 和 phone 自动加密 -->
    </insert>
    
    <select id="selectById" parameterType="java.lang.Long" 
            resultType="com.example.entity.User">
        SELECT * FROM user WHERE id = #{id}
        <!-- 结果自动解密 -->
    </select>
    
</mapper>
```

---

## 🔗 多表查询示例

### 示例1: 内连接查询

```java
@Mapper
public interface UserOrderMapper {
    
    @Select("SELECT u.id as user_id, u.name as user_name, " +
            "       o.id as order_id, o.order_no, o.customer_name " +
            "FROM user u " +
            "INNER JOIN orders o ON u.id = o.user_id " +
            "WHERE u.id = #{userId}")
    List<UserOrderDTO> selectUserOrders(@Param("userId") Long userId);
}

// DTO 类
public class UserOrderDTO {
    private Long userId;
    private String userName;      // 自动解密
    private Long orderId;
    private String orderNo;
    private String customerName;  // 自动解密
}
```

### 示例2: 左连接查询

```java
@Select("SELECT u.id, u.name, u.phone, o.order_no " +
        "FROM user u " +
        "LEFT JOIN orders o ON u.id = o.user_id")
List<Map<String, Object>> selectAllUsersWithOrders();

// 返回结果中，u.name、u.phone 会自动解密
```

### 示例3: 聚合查询

```java
@Select("SELECT u.name, COUNT(o.id) as order_count, SUM(o.amount) as total " +
        "FROM user u " +
        "LEFT JOIN orders o ON u.id = o.user_id " +
        "GROUP BY u.id, u.name")
List<Map<String, Object>> selectUserOrderStats();

// u.name 会自动解密
```

---

## ❓ 常见问题

### Q1: 启动时报错 "驱动未找到"

**原因**: 拦截器驱动未正确配置

**解决**: 确保数据源 URL 使用 `jdbc:interceptor:` 前缀：

```yaml
url: jdbc:interceptor:mysql://localhost:3306/test_db
#     ^^^^^^^^^^^^^^^^ 必须包含这个前缀
```

### Q2: 字段没有自动加密

**检查清单**:
1. ✅ `securtkit.encryptor.enable` 是否为 `true`
2. ✅ 表和字段名是否完全匹配（区分大小写）
3. ✅ 是否实现了 `FieldEncryptorStrategy`
4. ✅ 是否正确配置了数据源驱动

### Q3: 查询时字段没有解密

**可能原因**:
1. 字段未在配置中声明
2. SQL 中的字段别名未正确映射
3. 多表查询时字段来源表未正确识别

**解决**: 检查日志中的 SQL 解析信息，确认字段是否正确识别。

### Q4: 如何测试加密是否生效？

**方法1**: 直接查询数据库

```sql
-- 数据库中应该是加密后的值
SELECT name, phone FROM user WHERE id = 1;
```

**方法2**: 查看日志

框架会输出详细的 SQL 日志，可以看到加密后的值。

### Q5: 支持批量操作吗？

**支持！** 所有批量操作都会自动处理：

```java
// 批量插入
List<User> users = Arrays.asList(user1, user2, user3);
userMapper.insertBatch(users);  // 每个字段自动加密

// 批量更新
userMapper.updateBatchById(users);  // 每个字段自动加密
```

---

## 🎯 下一步

- 📖 阅读 [完整文档](./README.md)
- 🔧 查看 [架构设计](./ARCHITECTURE.md)
- 🧪 运行 [测试项目](./securt-kit-test/README.md)
- 💡 了解 [最佳实践](./README.md#最佳实践)

---

**祝你使用愉快！** 🚀

