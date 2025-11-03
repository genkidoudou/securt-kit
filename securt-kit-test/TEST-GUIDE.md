# Securt-Kit Test 测试指南

## 目录

- [测试概述](#测试概述)
- [测试环境](#测试环境)
- [测试用例说明](#测试用例说明)
- [运行测试](#运行测试)
- [测试结果验证](#测试结果验证)
- [常见问题](#常见问题)

---

## 测试概述

本项目提供了完整的测试用例，用于验证 Securt-Kit 数据库字段加密框架的功能。

### 测试类型

1. **单表操作测试**：验证基本的 CRUD 操作和字段加密解密
2. **多表查询测试**：验证复杂 SQL 查询场景下的加密解密功能
3. **集成测试**：验证与 Spring Boot 和 MyBatis 的集成

### 测试执行方式

测试使用 `CommandLineRunner` 接口，在应用启动时自动执行，无需使用 JUnit 测试框架。

---

## 测试环境

### 数据库

- **类型**: H2 内存数据库
- **模式**: MySQL 兼容模式
- **初始化**: 通过 `schema.sql` 和 `data.sql` 自动初始化

### 测试数据

#### 用户表数据

| ID | Name | Phone | Age | Email |
|----|------|-------|-----|-------|
| 1 | 张三(加密) | 13800138000(加密) | 25 | zhangsan@example.com |
| 2 | 李四(加密) | 13800138001(加密) | 30 | lisi@example.com |
| 3 | 王五(加密) | 13800138002(加密) | 28 | wangwu@example.com |

#### 订单表数据

| ID | User ID | Order No | Customer Name | Customer Phone | Amount | Status |
|----|---------|----------|---------------|----------------|--------|--------|
| 1 | 1 | ORD20250101001 | 张三(加密) | 13800138000(加密) | 199.99 | PAID |
| 2 | 1 | ORD20250101002 | 张三(加密) | 13800138000(加密) | 299.99 | SHIPPED |
| 3 | 2 | ORD20250101003 | 李四(加密) | 13800138001(加密) | 599.99 | DELIVERED |
| 4 | 2 | ORD20250101004 | 李四(加密) | 13800138001(加密) | 399.99 | PAID |
| 5 | 3 | ORD20250101005 | 王五(加密) | 13800138002(加密) | 99.99 | PENDING |

---

## 测试用例说明

### MyBatisTestRunner - 单表操作测试

**执行顺序**: `@Order(1)`

#### 测试1: 查询所有用户

**目的**: 验证查询时字段自动解密功能

**测试步骤**:
1. 调用 `userEntityMapper.selectList(null)` 查询所有用户
2. 验证返回的用户数据
3. 验证加密字段（name、phone）是否已自动解密

**预期结果**:
- 返回 3 条用户记录
- name 和 phone 字段显示为解密后的值
- 日志输出用户详细信息

**验证点**:
```java
allUsers.forEach(user -> {
    // name 和 phone 应该是解密后的值，不应该包含 "(加密)" 标记
    assert !user.getName().contains("(加密)");
    assert !user.getPhone().contains("(加密)");
});
```

---

#### 测试2: 根据ID查询用户

**目的**: 验证根据ID查询时的字段解密

**测试步骤**:
1. 获取第一个用户的ID
2. 调用 `userEntityMapper.selectById(userId)` 查询
3. 验证返回的用户信息

**预期结果**:
- 成功查询到用户
- 加密字段已自动解密

---

#### 测试3: 插入新用户

**目的**: 验证插入时字段自动加密功能

**测试步骤**:
1. 创建新的 UserEntity 对象
2. 设置 name 和 phone（明文）
3. 调用 `userEntityMapper.insert(user)` 插入
4. 验证插入结果

**预期结果**:
- 插入成功
- name 和 phone 字段在数据库中存储为加密值
- 新用户获得自动生成的ID

**验证方法**:
插入后立即查询，验证字段是否已加密存储。

---

#### 测试4: 更新用户信息

**目的**: 验证更新时字段自动加密功能

**测试步骤**:
1. 查询已存在的用户
2. 修改 name 和 age 字段
3. 调用 `userEntityMapper.updateById(user)` 更新
4. 再次查询验证更新结果

**预期结果**:
- 更新成功
- 修改的字段已重新加密存储
- 查询时字段自动解密

---

#### 测试5: 查询更新后的所有用户

**目的**: 验证更新后数据的一致性

**测试步骤**:
1. 调用 `userEntityMapper.selectList(null)` 查询所有用户
2. 验证更新后的用户数据

**预期结果**:
- 用户总数增加（包含新插入的用户）
- 所有加密字段正确解密

---

### MultiTableQueryTestRunner - 多表查询测试

**执行顺序**: `@Order(2)`

#### 测试1: 内连接查询

**目的**: 验证多表 JOIN 查询时字段加密解密

**测试步骤**:
1. 调用 `userOrderMapper.selectUserWithOrders(1L)` 查询用户ID=1的所有订单
2. 验证返回结果
3. 验证多个表的加密字段是否都正确解密

**SQL 示例**:
```sql
SELECT 
  u.name AS user_name,        -- 需要解密
  u.phone AS user_phone,      -- 需要解密
  o.customer_name,            -- 需要解密
  o.customer_phone           -- 需要解密
FROM user u
INNER JOIN orders o ON u.id = o.user_id
WHERE u.id = 1
```

**预期结果**:
- 返回用户ID=1的所有订单（2条记录）
- user 表和 orders 表的加密字段都正确解密

**验证点**:
```java
userOrders.forEach(dto -> {
    // 验证用户表的加密字段
    assert dto.getUserName() != null;
    assert !dto.getUserName().contains("(加密)");
    
    // 验证订单表的加密字段
    assert dto.getCustomerName() != null;
    assert !dto.getCustomerName().contains("(加密)");
});
```

---

#### 测试2: 左连接查询

**目的**: 验证 LEFT JOIN 查询，包含没有订单的用户

**测试步骤**:
1. 调用 `userOrderMapper.selectAllUsersWithOrders()` 查询
2. 验证返回结果包含所有用户
3. 验证有订单和无订单的用户数据

**预期结果**:
- 返回所有用户（3条用户记录，可能有多个订单）
- 没有订单的用户，订单相关字段为 null
- 加密字段正确解密

---

#### 测试3: 根据订单号查询

**目的**: 验证带条件的多表查询

**测试步骤**:
1. 调用 `userOrderMapper.selectUserOrderByOrderNo("ORD20250101001")` 查询
2. 验证返回的单条记录
3. 验证所有字段映射正确

**预期结果**:
- 返回指定订单号对应的用户和订单信息
- 所有加密字段正确解密

---

#### 测试4: 从订单表出发查询

**目的**: 验证反向多表查询

**测试步骤**:
1. 调用 `userOrderMapper.selectOrdersWithUsers()` 查询
2. 验证所有订单及其用户信息

**预期结果**:
- 返回所有订单（5条记录）
- 每个订单都包含对应的用户信息
- 加密字段正确解密

---

#### 测试5: 聚合统计查询

**目的**: 验证带聚合函数的复杂查询

**测试步骤**:
1. 调用 `userOrderMapper.selectUserOrderStats()` 查询
2. 验证统计结果
3. 验证聚合计算是否正确

**SQL 示例**:
```sql
SELECT 
  u.name AS user_name,
  COUNT(o.id) AS order_count,
  SUM(o.amount) AS total_amount
FROM user u
LEFT JOIN orders o ON u.id = o.user_id
GROUP BY u.id, u.name
```

**预期结果**:
- 返回每个用户的统计信息
- 订单数量正确
- 总金额正确
- 加密字段正确解密

---

#### 测试6: 插入新订单

**目的**: 验证多表插入时的字段加密

**测试步骤**:
1. 创建新的 OrderEntity 对象
2. 设置 customerName 和 customerPhone（明文）
3. 调用 `orderEntityMapper.insert(order)` 插入
4. 查询验证加密结果

**预期结果**:
- 插入成功
- customerName 和 customerPhone 字段已加密存储
- 查询时自动解密

---

## 运行测试

### 方式1: Maven 运行

```bash
cd securt-kit-test
mvn clean spring-boot:run
```

### 方式2: IDE 运行

1. 在 IDE 中打开项目
2. 找到 `com.example.TestApplication` 类
3. 运行 `main` 方法

### 方式3: 打包运行

```bash
mvn clean package
java -jar target/securt-kit-test-0.0.1-SNAPSHOT.jar
```

---

## 测试结果验证

### 查看测试输出

测试执行后，控制台会输出详细的测试日志：

```
========== MyBatis 测试开始 ==========
【测试1】查询所有用户
查询结果数量: 3
用户信息 - ID: 1, 姓名: 张三, 电话: 13800138000, 年龄: 25, 邮箱: zhangsan@example.com
...

========== 多表查询测试开始 ==========
【多表测试1】内连接查询 - 查询用户ID=1的所有订单
查询结果数量: 2
用户订单信息 - 用户ID: 1, 用户名: 张三, 用户电话: 13800138000, 订单号: ORD20250101001, ...
...
```

### 验证要点

1. **加密字段解密验证**:
   - 查询结果中的加密字段不应该包含 "(加密)" 标记
   - 字段值应该是可读的明文

2. **数据一致性验证**:
   - 插入后立即查询，验证数据是否正确
   - 更新后查询，验证修改是否生效

3. **多表查询验证**:
   - 不同表的加密字段都应该正确解密
   - 字段别名映射正确
   - 聚合计算结果正确

---

## 常见问题

### Q1: 测试执行失败，提示找不到表

**原因**: 数据库初始化失败

**解决方法**:
1. 检查 `schema.sql` 和 `data.sql` 文件是否存在
2. 检查 SQL 语法是否正确
3. 查看日志中的详细错误信息

---

### Q2: 加密字段没有自动解密

**可能原因**:
1. 配置文件中未正确配置加密字段
2. 字段名不匹配（注意大小写）
3. 表名配置错误

**解决方法**:
1. 检查 `application.yml` 中的 `securtkit.encryptor.tables` 配置
2. 确认字段名与数据库表字段名一致
3. 确认表名与数据库表名一致

---

### Q3: 多表查询时部分字段未解密

**可能原因**:
1. Mapper XML 中的字段别名未正确映射
2. resultMap 配置不完整

**解决方法**:
1. 检查 `UserOrderMapper.xml` 中的 `resultMap` 配置
2. 确认所有加密字段都包含在 `resultMap` 中
3. 检查字段别名是否正确

---

### Q4: 插入数据时报错

**可能原因**:
1. 外键约束失败
2. 字段长度超限
3. 必填字段未填写

**解决方法**:
1. 检查外键关联是否正确（如 orders 表的 user_id）
2. 检查字段长度限制
3. 确认必填字段都已设置

---

## 测试数据清理

### 自动清理

项目使用 H2 内存数据库，应用停止后数据自动清除。

### 手动重置

如需重置测试数据，重启应用即可。应用启动时会自动执行 `schema.sql` 和 `data.sql`。

---

## 性能测试

### 测试指标

- **插入性能**: 测试加密对插入操作的影响
- **查询性能**: 测试解密对查询操作的影响
- **多表查询性能**: 测试复杂查询的性能

### 性能建议

1. 只对敏感字段加密，避免全表加密
2. 使用批量操作提高效率
3. 合理配置连接池大小

---

## 扩展测试

### 添加新测试

如需添加新的测试用例：

1. 在对应的 TestRunner 类中添加测试方法
2. 使用 logger 输出测试结果
3. 验证加密解密功能

**示例**:

```java
// 测试7: 批量插入
logger.info("\n【测试7】批量插入用户");
List<UserEntity> users = new ArrayList<>();
// ... 添加用户数据
// 批量插入测试
```

---

## 测试覆盖率

当前测试覆盖：

- ✅ 单表 CRUD 操作
- ✅ 多表 JOIN 查询
- ✅ 条件查询
- ✅ 聚合查询
- ✅ 字段加密解密
- ✅ 字段别名映射

---

## 注意事项

1. **测试顺序**: 测试按照 `@Order` 注解的顺序执行，确保单表测试先于多表测试
2. **数据依赖**: 多表测试依赖单表测试插入的数据
3. **日志级别**: 建议使用 DEBUG 级别查看详细的 SQL 日志
4. **数据库状态**: 每次运行都会重新初始化数据库

---

## 相关文档

- [README.md](./README.md) - 项目主文档
- [API-DOCUMENTATION.md](./API-DOCUMENTATION.md) - API 文档

