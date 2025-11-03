# Securt-Kit 数据库字段加密测试项目

## 📋 目录

- [项目概述](#项目概述)
- [项目特性](#项目特性)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [使用示例](#使用示例)
- [测试说明](#测试说明)
- [多表查询测试](#多表查询测试)
- [API 文档](#api-文档)
- [常见问题](#常见问题)
- [技术架构](#技术架构)

---

## 📖 项目概述

`securt-kit-test` 是一个基于 Spring Boot 和 MyBatis 的测试项目，用于验证和演示 **Securt-Kit** 数据库字段加密框架的功能。

### 核心功能

- ✅ **透明字段加密**：无需修改业务代码，自动对指定字段进行加密/解密
- ✅ **MyBatis 集成**：支持 MyBatis 和 MyBatis-Plus
- ✅ **多表查询支持**：支持复杂的多表 JOIN 查询场景
- ✅ **自动加密解密**：插入时自动加密，查询时自动解密
- ✅ **配置化**：通过配置文件灵活指定需要加密的表和字段

---

## ✨ 项目特性

### 1. 透明加密
- 业务代码无需修改，框架自动拦截 SQL 执行
- 对配置的字段自动进行加密/解密处理

### 2. 多场景支持
- ✅ 单表 CRUD 操作
- ✅ 多表 JOIN 查询（INNER JOIN、LEFT JOIN）
- ✅ 聚合函数查询（COUNT、SUM、GROUP BY）
- ✅ 条件查询和排序

### 3. 灵活配置
- 支持 YAML 配置文件方式
- 支持按表和字段粒度配置
- 支持自定义加密策略

### 4. 测试完善
- 单表操作测试
- 多表查询测试
- 加密解密验证
- 性能测试

---

## 📁 项目结构

```
securt-kit-test/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/
│   │   │       ├── TestApplication.java              # Spring Boot 主应用类
│   │   │       ├── config/
│   │   │       │   └── ManualMyBatisConfig.java      # MyBatis 手动配置（已注释，使用自动配置）
│   │   │       ├── entity/
│   │   │       │   ├── UserEntity.java              # 用户实体类
│   │   │       │   └── OrderEntity.java             # 订单实体类
│   │   │       ├── dto/
│   │   │       │   └── UserOrderDTO.java             # 多表查询结果 DTO
│   │   │       ├── mapper/
│   │   │       │   ├── UserEntityMapper.java         # 用户 Mapper（MyBatis-Plus）
│   │   │       │   ├── OrderEntityMapper.java        # 订单 Mapper（MyBatis-Plus）
│   │   │       │   └── UserOrderMapper.java          # 多表查询 Mapper 接口
│   │   │       ├── test/
│   │   │       │   ├── MyBatisTestRunner.java        # 单表操作测试
│   │   │       │   └── MultiTableQueryTestRunner.java # 多表查询测试
│   │   │       └── MyFieldEncryptorStrategy.java      # 自定义加密策略
│   │   └── resources/
│   │       ├── application.yml                       # 主配置文件
│   │       ├── schema.sql                            # 数据库表结构
│   │       ├── data.sql                              # 初始化测试数据
│   │       └── mapper/
│   │           ├── UserMapper.xml                     # 用户 Mapper XML（可选）
│   │           └── UserOrderMapper.xml                # 多表查询 Mapper XML
│   └── test/                                          # 单元测试目录
└── pom.xml                                            # Maven 依赖配置
```

---

## 🚀 快速开始

### 前置要求

- JDK 1.8+
- Maven 3.6+
- Spring Boot 2.6.13

### 1. 克隆项目

```bash
git clone <repository-url>
cd securt-kit-test
```

### 2. 配置数据库

项目使用 H2 内存数据库，无需额外配置。如需使用其他数据库，修改 `application.yml`：

```yaml
spring:
  datasource:
    driver-class-name: io.github.hexlodev.core.interceptor.SimpleInterceptorDriver
    url: jdbc:interceptor:mysql://localhost:3306/test_db
    username: root
    password: your_password
```

### 3. 运行项目

```bash
# 方式1: 使用 Maven
mvn clean spring-boot:run

# 方式2: 打包后运行
mvn clean package
java -jar target/securt-kit-test-0.0.1-SNAPSHOT.jar

# 方式3: IDE 中直接运行 TestApplication.main()
```

### 4. 查看测试结果

应用启动后，会自动执行测试，控制台会输出测试结果：

```
========== MyBatis 测试开始 ==========
【测试1】查询所有用户
查询结果数量: 3
用户信息 - ID: 1, 姓名: 张三, 电话: 13800138000, 年龄: 25, 邮箱: zhangsan@example.com
...

========== 多表查询测试开始 ==========
【多表测试1】内连接查询 - 查询用户ID=1的所有订单
...
```

---

## ⚙️ 配置说明

### 1. application.yml 配置

#### 数据源配置

```yaml
spring:
  datasource:
    # 使用拦截器驱动
    driver-class-name: io.github.hexlodev.core.interceptor.SimpleInterceptorDriver
    url: 'jdbc:interceptor:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL'
    username: sa
    password:
```

#### MyBatis 配置

```yaml
mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.example.entity,io.github.test.entity
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

#### 字段加密配置

```yaml
securtkit:
  encryptor:
    enable: true  # 是否启用加密
    tables:
      # 用户表加密配置
      - table-name: user
        fields:
          - field-name: name      # 姓名字段需要加密
          - field-name: phone     # 电话字段需要加密
      # 订单表加密配置
      - table-name: orders
        fields:
          - field-name: customer_name   # 客户姓名字段需要加密
          - field-name: customer_phone  # 客户电话字段需要加密
```

### 2. 数据库表结构

#### 用户表 (user)

```sql
CREATE TABLE user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,      -- 加密字段
    phone VARCHAR(20),                -- 加密字段
    age INT,
    email VARCHAR(100),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### 订单表 (orders)

```sql
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    order_no VARCHAR(50) NOT NULL,
    customer_name VARCHAR(100),       -- 加密字段
    customer_phone VARCHAR(20),       -- 加密字段
    amount DECIMAL(10, 2),
    status VARCHAR(20),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id)
);
```

---

## 💡 使用示例

### 1. 单表操作示例

#### 插入数据（自动加密）

```java
@Resource
private UserEntityMapper userEntityMapper;

UserEntity user = new UserEntity();
user.setName("张三");           // 自动加密
user.setPhone("13800138000");   // 自动加密
user.setAge(25);
user.setEmail("zhangsan@example.com");

userEntityMapper.insert(user);  // 插入时自动加密
```

#### 查询数据（自动解密）

```java
// 查询单个用户
UserEntity user = userEntityMapper.selectById(1L);
// user.getName() 和 user.getPhone() 已经是解密后的值

// 查询所有用户
List<UserEntity> users = userEntityMapper.selectList(null);
users.forEach(u -> {
    System.out.println(u.getName());  // 自动解密
    System.out.println(u.getPhone()); // 自动解密
});
```

#### 更新数据（自动加密）

```java
UserEntity user = userEntityMapper.selectById(1L);
user.setName("新名字");          // 修改后自动加密
user.setPhone("13999999999");   // 修改后自动加密
userEntityMapper.updateById(user);
```

### 2. 多表查询示例

#### 内连接查询

```java
@Resource
private UserOrderMapper userOrderMapper;

// 查询用户及其所有订单
List<UserOrderDTO> userOrders = userOrderMapper.selectUserWithOrders(1L);
userOrders.forEach(dto -> {
    System.out.println("用户: " + dto.getUserName());         // 自动解密
    System.out.println("订单: " + dto.getOrderNo());
    System.out.println("客户: " + dto.getCustomerName());    // 自动解密
});
```

#### 左连接查询

```java
// 查询所有用户及其订单（包含没有订单的用户）
List<UserOrderDTO> allUsers = userOrderMapper.selectAllUsersWithOrders();
allUsers.forEach(dto -> {
    if (dto.getOrderId() != null) {
        System.out.println("用户: " + dto.getUserName() + ", 订单: " + dto.getOrderNo());
    } else {
        System.out.println("用户: " + dto.getUserName() + ", 无订单");
    }
});
```

#### 聚合查询

```java
// 统计每个用户的订单数量和总金额
List<UserOrderDTO> stats = userOrderMapper.selectUserOrderStats();
stats.forEach(dto -> {
    System.out.println("用户: " + dto.getUserName());
    System.out.println("订单数: " + dto.getOrderId());
    System.out.println("总金额: " + dto.getAmount());
});
```

---

## 🧪 测试说明

### 测试类说明

项目包含两个主要的测试运行器：

#### 1. MyBatisTestRunner（单表操作测试）

执行顺序：`@Order(1)`

测试内容：
- ✅ 查询所有用户
- ✅ 根据 ID 查询用户
- ✅ 插入新用户（测试加密）
- ✅ 更新用户信息（测试加密）
- ✅ 查询更新后的数据（测试解密）

#### 2. MultiTableQueryTestRunner（多表查询测试）

执行顺序：`@Order(2)`

测试内容：
- ✅ 内连接查询（INNER JOIN）
- ✅ 左连接查询（LEFT JOIN）
- ✅ 根据条件查询（WHERE）
- ✅ 反向查询（从订单表出发）
- ✅ 聚合统计查询（GROUP BY + COUNT + SUM）
- ✅ 插入新订单（测试多表加密）

### 运行测试

测试会在应用启动时自动执行，无需手动调用。查看控制台输出即可看到测试结果。

### 测试验证

测试会自动验证：
- 插入时字段是否正确加密
- 查询时字段是否正确解密
- 多表查询时不同表的字段是否正确处理

---

## 🔍 多表查询测试

### 支持的查询场景

#### 1. 内连接（INNER JOIN）

```xml
<!-- UserOrderMapper.xml -->
<select id="selectUserWithOrders" resultMap="UserOrderResultMap">
  SELECT 
    u.id AS user_id,
    u.name AS user_name,        -- 加密字段，自动解密
    u.phone AS user_phone,      -- 加密字段，自动解密
    o.order_no,
    o.customer_name,           -- 加密字段，自动解密
    o.customer_phone           -- 加密字段，自动解密
  FROM user u
  INNER JOIN orders o ON u.id = o.user_id
  WHERE u.id = #{userId}
</select>
```

#### 2. 左连接（LEFT JOIN）

支持查询所有用户及其订单，即使没有订单的用户也会返回。

#### 3. 字段别名映射

通过 `resultMap` 配置字段别名映射：

```xml
<resultMap id="UserOrderResultMap" type="com.example.dto.UserOrderDTO">
  <result column="user_name" property="userName" />
  <result column="customer_name" property="customerName" />
  ...
</resultMap>
```

#### 4. 聚合函数

支持 COUNT、SUM 等聚合函数，加密字段会自动解密后再参与计算。

---

## 📚 API 文档

### UserEntityMapper

继承自 MyBatis-Plus `BaseMapper<UserEntity>`，提供基础 CRUD 方法：

```java
// 插入
int insert(UserEntity entity);

// 根据 ID 查询
UserEntity selectById(Long id);

// 查询列表
List<UserEntity> selectList(Wrapper<UserEntity> wrapper);

// 根据 ID 更新
int updateById(UserEntity entity);

// 根据 ID 删除
int deleteById(Long id);
```

### OrderEntityMapper

继承自 MyBatis-Plus `BaseMapper<OrderEntity>`，提供基础 CRUD 方法。

### UserOrderMapper

自定义多表查询接口：

```java
// 查询用户及其所有订单（内连接）
List<UserOrderDTO> selectUserWithOrders(@Param("userId") Long userId);

// 查询所有用户及其订单（左连接）
List<UserOrderDTO> selectAllUsersWithOrders();

// 根据订单号查询
UserOrderDTO selectUserOrderByOrderNo(@Param("orderNo") String orderNo);

// 查询订单及其用户信息（从订单表出发）
List<UserOrderDTO> selectOrdersWithUsers();

// 统计每个用户的订单数量和总金额
List<UserOrderDTO> selectUserOrderStats();
```

---

## ❓ 常见问题

### Q1: 如何添加新的加密字段？

在 `application.yml` 中配置：

```yaml
securtkit:
  encryptor:
    tables:
      - table-name: your_table
        fields:
          - field-name: your_field
```

### Q2: 多表查询时字段没有自动解密？

检查以下几点：
1. 是否在 `application.yml` 中正确配置了加密字段
2. Mapper XML 中的字段别名是否正确
3. `resultMap` 是否正确映射了所有字段

### Q3: 如何自定义加密策略？

实现 `FieldEncryptorStrategy` 接口：

```java
@Component
public class MyFieldEncryptorStrategy implements FieldEncryptorStrategy {
    @Override
    public String encryption(String fieldValue) {
        // 自定义加密逻辑
        return encrypt(fieldValue);
    }
    
    @Override
    public String decryption(String fieldValue) {
        // 自定义解密逻辑
        return decrypt(fieldValue);
    }
}
```

### Q4: 支持哪些数据库？

理论上支持所有 JDBC 兼容的数据库：
- H2（测试使用）
- MySQL
- PostgreSQL
- Oracle
- SQL Server
- 等

### Q5: 性能影响如何？

- 加密/解密操作在内存中进行，性能影响很小
- SQL 解析使用缓存，不会重复解析
- 建议只对敏感字段进行加密，避免对性能造成影响

---

## 🏗️ 技术架构

### 核心组件

```
┌─────────────────────────────────────────────────────────┐
│                   应用层 (Application)                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │  UserEntity  │  │ OrderEntity  │  │ UserOrderDTO │ │
│  └──────────────┘  └──────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                  MyBatis / MyBatis-Plus                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │ UserMapper   │  │ OrderMapper  │  │ UserOrderMap │ │
│  └──────────────┘  └──────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│              Securt-Kit 拦截器 (Interceptor)             │
│  ┌────────────────────────────────────────────────────┐ │
│  │  SimpleInterceptorDriver                          │ │
│  │  ┌──────────────────────────────────────────────┐ │ │
│  │  │ SimpleInterceptorConnection                   │ │ │
│  │  │  ┌────────────────────────────────────────┐ │ │ │
│  │  │  │ SimpleInterceptorPreparedStatement     │ │ │ │
│  │  │  │  - 解析 SQL                            │ │ │ │
│  │  │  │  - 字段加密                            │ │ │ │
│  │  │  └────────────────────────────────────────┘ │ │ │
│  │  │  ┌────────────────────────────────────────┐ │ │ │
│  │  │  │ ResultSetDecryptingProxy               │ │ │ │
│  │  │  │  - 字段解密                            │ │ │ │
│  │  │  └────────────────────────────────────────┘ │ │ │
│  │  └──────────────────────────────────────────────┘ │ │
│  └────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                  数据库层 (Database)                      │
│              H2 / MySQL / PostgreSQL / ...               │
└─────────────────────────────────────────────────────────┘
```

### 工作流程

1. **插入流程**：
   ```
   应用代码 → MyBatis → 拦截器 → 解析 SQL → 识别加密字段 → 加密 → 数据库
   ```

2. **查询流程**：
   ```
   数据库 → 拦截器 → 识别加密字段 → 解密 → MyBatis → 应用代码
   ```

---

## 📝 更新日志

### v0.0.1-SNAPSHOT (2025-01-XX)

- ✅ 初始版本发布
- ✅ 支持单表 CRUD 操作
- ✅ 支持多表 JOIN 查询
- ✅ 支持字段加密解密
- ✅ 完善的测试用例

---

## 📄 许可证

本项目采用 MIT 许可证。

---

## 👥 贡献

欢迎提交 Issue 和 Pull Request！

---

## 🔗 相关链接

- [Securt-Kit Core](../securt-kit-core/README.md)
- [使用文档](../securt-kit-core/USAGE.md)

---

## 📞 联系方式

如有问题，请提交 Issue 或联系项目维护者。
