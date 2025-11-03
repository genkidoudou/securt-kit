# Securt-Kit Test API 文档

## 目录

- [实体类 API](#实体类-api)
- [Mapper 接口 API](#mapper-接口-api)
- [DTO 类 API](#dto-类-api)
- [测试类 API](#测试类-api)
- [配置类 API](#配置类-api)

---

## 实体类 API

### UserEntity

用户实体类，对应数据库 `user` 表。

#### 字段说明

| 字段名 | 类型 | 说明 | 是否加密 |
|--------|------|------|----------|
| id | Long | 主键ID | ❌ |
| name | String | 姓名 | ✅ |
| phone | String | 电话 | ✅ |
| age | Integer | 年龄 | ❌ |
| email | String | 邮箱 | ❌ |

#### 方法说明

```java
// Getter/Setter 方法
Long getId()
void setId(Long id)
String getName()
void setName(String name)
String getPhone()
void setPhone(String phone)
Integer getAge()
void setAge(Integer age)
String getEmail()
void setEmail(String email)

// toString 方法
String toString()
```

#### 使用示例

```java
UserEntity user = new UserEntity();
user.setName("张三");
user.setPhone("13800138000");
user.setAge(25);
user.setEmail("zhangsan@example.com");
```

---

### OrderEntity

订单实体类，对应数据库 `orders` 表。

#### 字段说明

| 字段名 | 类型 | 说明 | 是否加密 |
|--------|------|------|----------|
| id | Long | 主键ID | ❌ |
| userId | Long | 用户ID（外键） | ❌ |
| orderNo | String | 订单号 | ❌ |
| customerName | String | 客户姓名 | ✅ |
| customerPhone | String | 客户电话 | ✅ |
| amount | BigDecimal | 订单金额 | ❌ |
| status | String | 订单状态 | ❌ |
| createdTime | Timestamp | 创建时间 | ❌ |
| updatedTime | Timestamp | 更新时间 | ❌ |

#### 方法说明

```java
// Getter/Setter 方法
Long getId()
void setId(Long id)
Long getUserId()
void setUserId(Long userId)
String getOrderNo()
void setOrderNo(String orderNo)
String getCustomerName()
void setCustomerName(String customerName)
String getCustomerPhone()
void setCustomerPhone(String customerPhone)
BigDecimal getAmount()
void setAmount(BigDecimal amount)
String getStatus()
void setStatus(String status)
Timestamp getCreatedTime()
void setCreatedTime(Timestamp createdTime)
Timestamp getUpdatedTime()
void setUpdatedTime(Timestamp updatedTime)

// toString 方法
String toString()
```

---

## Mapper 接口 API

### UserEntityMapper

用户 Mapper 接口，继承自 `BaseMapper<UserEntity>`。

#### 继承的方法

```java
// 插入
int insert(UserEntity entity)

// 根据ID查询
UserEntity selectById(Serializable id)

// 查询列表
List<UserEntity> selectList(Wrapper<UserEntity> queryWrapper)

// 根据ID更新
int updateById(UserEntity entity)

// 根据ID删除
int deleteById(Serializable id)

// 批量插入
int insertBatch(List<UserEntity> entityList)

// 批量更新
int updateBatchById(List<UserEntity> entityList)

// 批量删除
int deleteBatchIds(Collection<? extends Serializable> idList)
```

#### 使用示例

```java
@Resource
private UserEntityMapper userMapper;

// 插入
UserEntity user = new UserEntity();
user.setName("张三");
userMapper.insert(user);

// 查询
UserEntity user = userMapper.selectById(1L);
List<UserEntity> users = userMapper.selectList(null);

// 更新
user.setName("李四");
userMapper.updateById(user);

// 删除
userMapper.deleteById(1L);
```

---

### OrderEntityMapper

订单 Mapper 接口，继承自 `BaseMapper<OrderEntity>`。

#### 方法说明

与 `UserEntityMapper` 相同，继承自 `BaseMapper<OrderEntity>`。

---

### UserOrderMapper

多表查询 Mapper 接口。

#### selectUserWithOrders

查询指定用户的所有订单（内连接）。

**方法签名：**

```java
List<UserOrderDTO> selectUserWithOrders(@Param("userId") Long userId)
```

**参数：**
- `userId` (Long): 用户ID

**返回值：**
- `List<UserOrderDTO>`: 用户订单列表

**SQL 示例：**

```sql
SELECT 
  u.id AS user_id,
  u.name AS user_name,
  u.phone AS user_phone,
  o.id AS order_id,
  o.order_no,
  o.customer_name,
  o.customer_phone,
  o.amount,
  o.status AS order_status
FROM user u
INNER JOIN orders o ON u.id = o.user_id
WHERE u.id = ?
ORDER BY o.id DESC
```

**使用示例：**

```java
List<UserOrderDTO> orders = userOrderMapper.selectUserWithOrders(1L);
```

---

#### selectAllUsersWithOrders

查询所有用户及其订单（左连接）。

**方法签名：**

```java
List<UserOrderDTO> selectAllUsersWithOrders()
```

**返回值：**
- `List<UserOrderDTO>`: 用户订单列表（包含没有订单的用户）

**使用示例：**

```java
List<UserOrderDTO> allUsers = userOrderMapper.selectAllUsersWithOrders();
```

---

#### selectUserOrderByOrderNo

根据订单号查询用户和订单信息。

**方法签名：**

```java
UserOrderDTO selectUserOrderByOrderNo(@Param("orderNo") String orderNo)
```

**参数：**
- `orderNo` (String): 订单号

**返回值：**
- `UserOrderDTO`: 用户订单信息，如果不存在返回 `null`

**使用示例：**

```java
UserOrderDTO orderInfo = userOrderMapper.selectUserOrderByOrderNo("ORD20250101001");
```

---

#### selectOrdersWithUsers

查询所有订单及其用户信息（从订单表出发）。

**方法签名：**

```java
List<UserOrderDTO> selectOrdersWithUsers()
```

**返回值：**
- `List<UserOrderDTO>`: 订单用户列表

**使用示例：**

```java
List<UserOrderDTO> orders = userOrderMapper.selectOrdersWithUsers();
```

---

#### selectUserOrderStats

统计每个用户的订单数量和总金额。

**方法签名：**

```java
List<UserOrderDTO> selectUserOrderStats()
```

**返回值：**
- `List<UserOrderDTO>`: 统计结果列表
  - `userId`: 用户ID
  - `userName`: 用户名
  - `orderId`: 订单数量（使用 orderId 字段存储）
  - `amount`: 总金额

**使用示例：**

```java
List<UserOrderDTO> stats = userOrderMapper.selectUserOrderStats();
stats.forEach(dto -> {
    System.out.println("用户: " + dto.getUserName());
    System.out.println("订单数: " + dto.getOrderId());
    System.out.println("总金额: " + dto.getAmount());
});
```

---

## DTO 类 API

### UserOrderDTO

用户订单查询结果 DTO，用于多表查询结果映射。

#### 字段说明

| 字段名 | 类型 | 说明 | 来源表 |
|--------|------|------|--------|
| userId | Long | 用户ID | user |
| userName | String | 用户名（加密） | user |
| userPhone | String | 用户电话（加密） | user |
| userAge | Integer | 用户年龄 | user |
| userEmail | String | 用户邮箱 | user |
| orderId | Long | 订单ID | orders |
| orderNo | String | 订单号 | orders |
| customerName | String | 客户姓名（加密） | orders |
| customerPhone | String | 客户电话（加密） | orders |
| amount | BigDecimal | 订单金额 | orders |
| orderStatus | String | 订单状态 | orders |

#### 方法说明

所有字段都提供了标准的 Getter/Setter 方法。

**使用示例：**

```java
UserOrderDTO dto = userOrderMapper.selectUserOrderByOrderNo("ORD001");
System.out.println("用户: " + dto.getUserName());
System.out.println("订单: " + dto.getOrderNo());
```

---

## 测试类 API

### MyBatisTestRunner

单表操作测试运行器。

**执行顺序：** `@Order(1)`

**测试内容：**
1. 查询所有用户
2. 根据ID查询用户
3. 插入新用户
4. 更新用户信息
5. 查询更新后的数据

**自动执行：** 应用启动时自动执行

---

### MultiTableQueryTestRunner

多表查询测试运行器。

**执行顺序：** `@Order(2)`

**测试内容：**
1. 内连接查询
2. 左连接查询
3. 根据订单号查询
4. 从订单表出发查询
5. 聚合统计查询
6. 插入新订单

**自动执行：** 应用启动时自动执行

**验证方法：**

```java
private void verifyDecryption(String fieldDesc, String fieldValue)
```

验证字段是否正确解密。

---

## 配置类 API

### TestApplication

Spring Boot 主应用类。

**注解：**
- `@SpringBootApplication`
- `@MapperScan(basePackages = {"com.example.mapper", "io.github.test.mapper"})`

**方法：**

```java
public static void main(String[] args)
```

应用入口方法。

---

## 数据库字段映射

### user 表字段映射

| 数据库字段 | Java 字段 | 类型 | 加密 |
|-----------|----------|------|------|
| id | id | Long | ❌ |
| name | name | String | ✅ |
| phone | phone | String | ✅ |
| age | age | Integer | ❌ |
| email | email | String | ❌ |

### orders 表字段映射

| 数据库字段 | Java 字段 | 类型 | 加密 |
|-----------|----------|------|------|
| id | id | Long | ❌ |
| user_id | userId | Long | ❌ |
| order_no | orderNo | String | ❌ |
| customer_name | customerName | String | ✅ |
| customer_phone | customerPhone | String | ✅ |
| amount | amount | BigDecimal | ❌ |
| status | status | String | ❌ |

---

## 错误处理

### 常见异常

1. **SQLException**: 数据库操作异常
2. **IllegalArgumentException**: 参数验证失败
3. **RuntimeException**: SQL 解析失败或其他运行时错误

### 异常处理建议

```java
try {
    UserEntity user = userMapper.selectById(id);
} catch (Exception e) {
    logger.error("查询用户失败", e);
    // 处理异常
}
```

---

## 性能建议

1. **只对敏感字段加密**：避免对大量字段加密，影响性能
2. **使用连接池**：配置合适的数据库连接池大小
3. **批量操作**：优先使用批量插入/更新方法
4. **索引优化**：对经常查询的字段创建索引

---

## 版本信息

- **当前版本**: 0.0.1-SNAPSHOT
- **Spring Boot**: 2.6.13
- **MyBatis-Plus**: 3.5.8
- **MyBatis**: 3.5.16

---

## 更新历史

### v0.0.1-SNAPSHOT

- 初始版本
- 支持单表 CRUD
- 支持多表查询
- 完善的 API 文档

