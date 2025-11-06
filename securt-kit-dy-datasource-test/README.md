# 多数据源测试模块

## 📋 概述

`securt-kit-dy-datasource-test` 是一个完整的可运行应用，用于演示和测试 securt-kit 在多数据源场景下的字段加密/解密功能。

### 功能特性

- ✅ **多数据源支持**：支持三个独立的数据源（primary、secondary、third）
- ✅ **差异化加密配置**：不同数据源可以配置不同的加密字段
- ✅ **MyBatis-Plus 集成**：使用 `dynamic-datasource` 实现多数据源路由
- ✅ **复杂场景测试**：包含批量操作、事务、复杂查询等测试场景
- ✅ **REST API 测试**：提供完整的 REST API 用于功能验证

### 数据源配置说明

| 数据源 | 加密字段 | 说明 |
|--------|---------|------|
| **primary** | `name`、`phone` | 主数据源，加密两个字段 |
| **secondary** | `name` | 从数据源，只加密 name 字段 |
| **third** | 无 | 第三方数据源，不加密任何字段 |

---

## 🚀 快速开始

### 1. 启动应用

```bash
cd securt-kit-dy-datasource-test
mvn spring-boot:run
```

### 2. 访问测试接口

应用启动后，访问以下地址进行测试：

#### 基础测试接口

- **主数据源测试**：`http://localhost:8081/test/primary`
- **从数据源测试**：`http://localhost:8081/test/secondary`
- **第三方数据源测试**：`http://localhost:8081/test/third`
- **所有数据源测试**：`http://localhost:8081/test/all`

#### 复杂场景测试接口

详细接口列表请参考：[COMPLEX-TEST-GUIDE.md](./COMPLEX-TEST-GUIDE.md)

---

## 📁 项目结构

```
securt-kit-dy-datasource-test/
├── src/main/java/io/github/test/
│   ├── MultiDataSourceApplication.java      # 应用启动类
│   ├── MyFieldEncryptorStrategy.java        # 加密策略实现
│   ├── config/
│   │   └── DatabaseInitializer.java         # 数据库初始化器
│   ├── controller/
│   │   ├── TestController.java              # 基础测试控制器
│   │   └── ComplexTestController.java       # 复杂场景测试控制器
│   ├── entity/
│   │   ├── UserEntity.java                  # 用户实体类
│   │   └── OrderEntity.java                 # 订单实体类
│   ├── mapper/
│   │   ├── UserEntityMapper.java            # 用户 Mapper
│   │   └── OrderEntityMapper.java           # 订单 Mapper
│   └── service/
│       ├── MultiDataSourceUserService.java  # 多数据源用户服务
│       ├── ComplexQueryService.java          # 复杂查询服务
│       ├── BatchOperationService.java        # 批量操作服务
│       ├── TransactionService.java           # 事务服务
│       └── AdvancedSqlService.java           # 高级SQL服务
├── src/main/resources/
│   ├── application.yml                       # 应用配置文件
│   └── mapper/
│       └── UserMapper.xml                    # MyBatis Mapper XML
└── README.md                                 # 本文档
```

---

## ⚙️ 配置说明

### 1. 数据源配置

在 `application.yml` 中配置多数据源：

```yaml
spring:
  datasource:
    dynamic:
      primary: primary
      datasource:
        primary:
          driver-class-name: io.github.hexlodev.core.interceptor.SimpleInterceptorDriver
          url: 'jdbc:interceptor:h2:mem:primary_db;DB_CLOSE_DELAY=-1;MODE=MySQL;datasource-id=primary'
          username: sa
          password:
        secondary:
          driver-class-name: io.github.hexlodev.core.interceptor.SimpleInterceptorDriver
          url: 'jdbc:interceptor:h2:mem:secondary_db;DB_CLOSE_DELAY=-1;MODE=MySQL;datasource-id=secondary'
          username: sa
          password:
        third:
          driver-class-name: io.github.hexlodev.core.interceptor.SimpleInterceptorDriver
          url: 'jdbc:interceptor:h2:mem:third_db;DB_CLOSE_DELAY=-1;MODE=MySQL;datasource-id=third'
          username: sa
          password:
```

**关键点**：
- 使用 `SimpleInterceptorDriver` 作为驱动类
- URL 格式：`jdbc:interceptor:{真实数据库URL};datasource-id={数据源标识}`
- 通过 URL 参数传递 `datasource-id`，用于标识数据源

### 2. 加密配置

```yaml
securtkit:
  encryptor:
    enable: true
    failure-policy: FALLBACK
    sql-parse-cache:
      enable: true
      max-size: 1000
    tables:
      # 主数据源配置（primary）：加密 name 和 phone
      - table-name: user
        datasource-id: primary
        fields:
          - field-name: name
          - field-name: phone
      
      # 从数据源配置（secondary）：只加密 name
      - table-name: user
        datasource-id: secondary
        fields:
          - field-name: name
      
      # 第三方数据源不配置（等于关闭加密）
```

**配置说明**：
- `datasource-id` 字段是可选的
- 如果指定了 `datasource-id`，则该表配置只应用到该数据源
- 如果不指定 `datasource-id`，则该表配置应用到所有数据源

---

## 📖 使用示例

### 1. 基础使用

#### 在 Service 中使用 @DS 注解切换数据源

```java
@Service
public class MultiDataSourceUserService {
    
    @Autowired
    private UserEntityMapper userEntityMapper;
    
    /**
     * 在主数据源中保存用户（加密 name 和 phone）
     */
    @DS("primary")
    public int saveToPrimary(UserEntity user) {
        return userEntityMapper.insert(user);
    }
    
    /**
     * 在主数据源中查询用户（自动解密 name 和 phone）
     */
    @DS("primary")
    public UserEntity getFromPrimary(Long id) {
        return userEntityMapper.selectById(id);
    }
    
    /**
     * 在从数据源中保存用户（只加密 name，不加密 phone）
     */
    @DS("secondary")
    public int saveToSecondary(UserEntity user) {
        return userEntityMapper.insert(user);
    }
}
```

### 2. 复杂场景使用

#### 批量操作

```java
@Autowired
private BatchOperationService batchOperationService;

// 批量插入到主数据源
List<UserEntity> users = Arrays.asList(user1, user2, user3);
int count = batchOperationService.batchInsertToPrimary(users);
```

#### 事务操作

```java
@Autowired
private TransactionService transactionService;

// 在主数据源中创建用户和订单（事务）
UserEntity user = new UserEntity();
OrderEntity order = new OrderEntity();
transactionService.createUserAndOrderInPrimary(user, order);
```

#### 复杂查询

```java
@Autowired
private ComplexQueryService complexQueryService;

// JOIN 查询（主数据源）
List<OrderEntity> orders = complexQueryService.findOrdersByUserNameInPrimary("张三");
```

---

## 🧪 测试场景

### 1. 基础功能测试

- ✅ 单数据源加密/解密
- ✅ 多数据源配置隔离
- ✅ 字段级别加密配置

### 2. 复杂场景测试

详细测试场景请参考：[COMPLEX-TEST-GUIDE.md](./COMPLEX-TEST-GUIDE.md)

包括：
- 复杂查询（JOIN、子查询、聚合函数）
- 批量操作（批量插入、更新、查询、删除）
- 事务操作（单数据源事务、跨数据源操作）
- 高级SQL（ORDER BY、GROUP BY、分页查询）

---

## 📚 API 文档

### 基础测试 API

#### GET /test/primary
测试主数据源（加密 name 和 phone）

**响应示例**：
```json
{
  "insertResult": 1,
  "insertedUser": {
    "id": 1,
    "name": "张三",
    "phone": "13800138000",
    "age": 25,
    "email": "zhangsan@example.com"
  },
  "queriedUser": {
    "id": 1,
    "name": "张三",
    "phone": "13800138000",
    "age": 25,
    "email": "zhangsan@example.com"
  },
  "allUsers": [...],
  "totalCount": 1,
  "message": "主数据源测试完成（name 和 phone 字段应该被加密）"
}
```

#### GET /test/secondary
测试从数据源（只加密 name）

#### GET /test/third
测试第三方数据源（不加密）

#### GET /test/all
测试所有数据源

### 复杂场景测试 API

详细 API 文档请参考：[COMPLEX-TEST-GUIDE.md](./COMPLEX-TEST-GUIDE.md)

---

## 🔍 验证方法

### 1. 验证加密功能

#### 方法1：查看日志

启用 DEBUG 日志级别，查看加密过程：

```yaml
logging:
  level:
    io.github.hexlodev.core: DEBUG
```

关键日志：
- `PreparedStatement created with datasource-id: X` - 确认 datasource-id 传递正确
- `fieldsCount=X` - 确认识别到需要加密的字段数量
- `Encrypted field: X in table: Y` - 确认字段加密成功

#### 方法2：直接查询数据库

```java
// 直接查询数据库（绕过拦截器）
try (Connection conn = dataSource.getConnection();
     Statement stmt = conn.createStatement();
     ResultSet rs = stmt.executeQuery("SELECT name, phone FROM \"user\"")) {
    while (rs.next()) {
        String name = rs.getString("name");
        String phone = rs.getString("phone");
        // 如果数据是加密的，这里的值应该不等于原始值
        System.out.println("name: " + name + ", phone: " + phone);
    }
}
```

### 2. 验证解密功能

查询数据后，检查返回结果中的字段值是否为明文。

### 3. 验证配置隔离

- 在主数据源中插入的数据，在从数据源中查询不到
- 不同数据源的加密配置互不影响

---

## ⚠️ 注意事项

### 1. 数据源标识传递

**推荐方式**：通过 URL 参数传递 `datasource-id`（通用方式，适用于所有连接池）：

```yaml
url: 'jdbc:interceptor:h2:mem:primary_db;datasource-id=primary'
```

**说明**：
- `connection-properties` 和 `hikari.data-source-properties` 中的属性不会被传递到 JDBC 驱动的 `connect(String url, Properties info)` 方法的 `info` 参数中
- URL 参数方式是最可靠的通用解决方案

### 2. H2 数据库保留关键字

H2 数据库中 `user` 和 `order` 是保留关键字，需要在 SQL 中使用双引号括起来：

```java
@TableName("\"user\"")
public class UserEntity {
    // ...
}
```

### 3. 表名配置

配置中的表名不需要双引号，系统会自动处理：

```yaml
tables:
  - table-name: user  # 不需要双引号
    fields:
      - field-name: name
```

---

## 🐛 常见问题

### Q1: 为什么插入时字段没有加密？

**A:** 检查以下几点：
1. 配置中的 `datasource-id` 是否正确
2. URL 中的 `datasource-id` 参数是否正确传递
3. 字段名是否匹配（注意大小写）
4. 查看日志确认 `datasource-id` 是否正确传递

### Q2: 为什么查询时字段没有解密？

**A:** 检查以下几点：
1. `datasource-id` 是否正确传递到 `ResultSetDecryptingProxy`
2. 字段是否在配置中正确声明
3. 查看日志确认解密过程

### Q3: 如何验证数据确实被加密了？

**A:** 直接查询数据库（不使用拦截器）来验证：
```java
// 直接查询数据库（绕过拦截器）
try (Connection conn = dataSource.getConnection();
     Statement stmt = conn.createStatement();
     ResultSet rs = stmt.executeQuery("SELECT name, phone FROM \"user\"")) {
    // 如果数据是加密的，这里的值应该不等于原始值
}
```

### Q4: @DS 注解如何与数据源标识关联？

**A:** `@DS("primary")` 注解的值必须与配置中的数据源名称和 URL 中的 `datasource-id` 参数值一致：

```yaml
datasource:
  primary:  # ← 数据源名称
    url: 'jdbc:interceptor:h2:mem:db;datasource-id=primary'  # ← URL 中的参数值
```

```java
@DS("primary")  // ← 这里的值必须与上面两个一致
public void method() {
    // ...
}
```

---

## 📖 相关文档

- [复杂场景测试指南](./COMPLEX-TEST-GUIDE.md) - 详细测试场景说明
- [多数据源设计方案](../MULTI-DATASOURCE-DESIGN.md) - 多数据源设计文档
- [主项目 README](../README.md) - 项目主文档
- [Core 模块文档](../securt-kit-core/README.md) - 核心模块文档

---

## 📝 更新日志

### v1.1.0 (当前版本)

- ✅ 支持多数据源配置
- ✅ 支持差异化加密配置
- ✅ 集成 MyBatis-Plus dynamic-datasource
- ✅ 添加复杂场景测试（批量操作、事务、复杂查询）
- ✅ 提供完整的 REST API 测试接口

---

**最后更新**: 2025-11-06  
**项目版本**: v1.1.0
