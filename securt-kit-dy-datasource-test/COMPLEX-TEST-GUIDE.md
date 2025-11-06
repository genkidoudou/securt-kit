# 复杂多数据源测试指南

本文档介绍如何使用复杂场景的多数据源测试类来验证加密/解密功能。

## 测试类概览

### 1. ComplexQueryService - 复杂查询测试
测试复杂SQL场景下的多数据源加密/解密功能。

**测试场景：**
- JOIN 查询（WHERE 条件中包含加密字段）
- 聚合查询（COUNT、SUM）
- 复杂 WHERE 条件（AND、OR）
- LIKE 查询
- IN 查询
- 范围查询（BETWEEN）

**API 端点：**
- `GET /api/complex/join/primary?userName=张三` - JOIN查询（主数据源）
- `GET /api/complex/count/primary?userId=1` - 聚合查询（主数据源）
- `GET /api/complex/where/primary?name=张三&phone=13800138000` - 复杂WHERE查询（主数据源）
- `GET /api/complex/like/primary?pattern=张` - LIKE查询（主数据源）

### 2. BatchOperationService - 批量操作测试
测试批量操作场景下的多数据源加密功能。

**测试场景：**
- 批量插入
- 批量更新
- 批量查询
- 批量删除
- 创建测试数据

**API 端点：**
- `POST /api/complex/batch/insert/primary` - 批量插入（主数据源）
- `POST /api/complex/batch/insert/secondary` - 批量插入（从数据源）
- `GET /api/complex/batch/select/primary?ids=1,2,3` - 批量查询（主数据源）
- `POST /api/complex/test-data/primary?count=10` - 创建测试数据（主数据源）

**请求示例：**
```json
POST /api/complex/batch/insert/primary
Content-Type: application/json

[
  {
    "name": "用户1",
    "phone": "13800000001",
    "age": 25,
    "email": "user1@example.com"
  },
  {
    "name": "用户2",
    "phone": "13800000002",
    "age": 26,
    "email": "user2@example.com"
  }
]
```

### 3. TransactionService - 事务测试
测试事务场景下的多数据源加密功能。

**测试场景：**
- 单数据源事务（创建用户和订单）
- 事务回滚场景
- 跨数据源操作（非事务）
- 批量事务操作
- 复杂事务操作（查询、插入、更新）

**API 端点：**
- `POST /api/complex/transaction/primary` - 事务操作（主数据源）
- `POST /api/complex/cross-datasource` - 跨数据源操作

**请求示例：**
```json
POST /api/complex/transaction/primary
Content-Type: application/json

{
  "user": {
    "name": "张三",
    "phone": "13800138000",
    "age": 25,
    "email": "zhangsan@example.com"
  },
  "order": {
    "orderNo": "ORD001",
    "amount": "100.00",
    "address": "北京市朝阳区",
    "status": "PENDING"
  }
}
```

### 4. AdvancedSqlService - 高级SQL测试
测试高级SQL场景下的多数据源加密功能。

**测试场景：**
- 子查询
- EXISTS / NOT EXISTS
- ORDER BY 加密字段
- GROUP BY 查询
- 多条件查询（AND、OR）
- NULL 值处理
- 分页查询

**API 端点：**
- `GET /api/complex/order-by/primary` - ORDER BY查询（主数据源）
- `GET /api/complex/page/primary?pageNum=1&pageSize=10` - 分页查询（主数据源）
- `GET /api/complex/multi-condition/primary?name=张三&phone=13800138000&minAge=20` - 多条件查询（主数据源）

## 数据源配置说明

### 主数据源（primary）
- **加密字段：** `name`、`phone`
- **测试点：** 插入时加密，查询时解密

### 从数据源（secondary）
- **加密字段：** `name`（仅此字段）
- **测试点：** 插入时只加密 `name`，`phone` 保持明文；查询时只解密 `name`

### 第三方数据源（third）
- **加密字段：** 无
- **测试点：** 所有字段都不加密，保持原样

## 测试步骤

### 1. 启动应用
```bash
cd securt-kit-dy-datasource-test
mvn spring-boot:run
```

### 2. 测试复杂查询
```bash
# JOIN查询
curl "http://localhost:8081/api/complex/join/primary?userName=张三"

# 聚合查询
curl "http://localhost:8081/api/complex/count/primary?userId=1"

# LIKE查询
curl "http://localhost:8081/api/complex/like/primary?pattern=张"
```

### 3. 测试批量操作
```bash
# 创建测试数据
curl -X POST "http://localhost:8081/api/complex/test-data/primary?count=10"

# 批量查询
curl "http://localhost:8081/api/complex/batch/select/primary?ids=1,2,3"
```

### 4. 测试事务
```bash
# 事务操作
curl -X POST "http://localhost:8081/api/complex/transaction/primary" \
  -H "Content-Type: application/json" \
  -d '{
    "user": {
      "name": "张三",
      "phone": "13800138000",
      "age": 25,
      "email": "zhangsan@example.com"
    },
    "order": {
      "orderNo": "ORD001",
      "amount": "100.00",
      "address": "北京市朝阳区",
      "status": "PENDING"
    }
  }'
```

### 5. 测试高级SQL
```bash
# ORDER BY查询
curl "http://localhost:8081/api/complex/order-by/primary"

# 分页查询
curl "http://localhost:8081/api/complex/page/primary?pageNum=1&pageSize=10"
```

## 验证要点

### 1. 加密验证
- 检查日志中的 `[FINAL SQL]`，确认加密字段的值是加密后的字符串
- 直接查询数据库，确认加密字段存储的是加密值

### 2. 解密验证
- 检查 API 返回结果，确认加密字段的值是明文
- 检查日志中的查询结果，确认解密成功

### 3. 多数据源隔离验证
- 在主数据源中插入的数据，在从数据源中查询不到
- 不同数据源的加密配置互不影响

### 4. 事务验证
- 事务中的多个操作都应该正确加密
- 事务回滚时，加密操作也应该正常执行

## 常见问题

### Q1: 为什么批量插入时有些字段没有加密？
**A:** 检查配置中的 `datasource-id` 是否正确，以及字段名是否匹配。

### Q2: 为什么查询结果中的字段没有解密？
**A:** 检查 `datasource-id` 是否正确传递，以及 `ResultSetDecryptingProxy` 是否正确包装了 `ResultSet`。

### Q3: 为什么事务中的操作没有加密？
**A:** 检查 `@DS` 注解是否正确，以及 `datasource-id` 是否正确传递到 `PreparedStatement`。

## 日志查看

启用 DEBUG 日志级别，可以查看详细的加密/解密过程：

```yaml
logging:
  level:
    io.github.hexlodev.core: DEBUG
    io.github.test: DEBUG
```

关键日志：
- `PreparedStatement created with datasource-id: X` - 确认 datasource-id 传递正确
- `fieldsCount=X` - 确认识别到需要加密的字段数量
- `Encrypted field: X in table: Y` - 确认字段加密成功
- `Decrypted field: X in table: Y` - 确认字段解密成功

