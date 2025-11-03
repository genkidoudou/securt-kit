# Securt-Kit 架构设计文档

## 目录

- [架构概述](#架构概述)
- [核心组件](#核心组件)
- [设计模式](#设计模式)
- [数据流程](#数据流程)
- [扩展点](#扩展点)
- [性能优化](#性能优化)

---

## 架构概述

Securt-Kit 采用分层架构设计，通过 JDBC 驱动拦截技术实现透明的字段加密解密功能。

### 架构层次

```
┌─────────────────────────────────────────┐
│        应用层 (Application Layer)         │
│  MyBatis / JdbcTemplate / ORM 框架      │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│      拦截层 (Interceptor Layer)           │
│  JDBC 驱动代理和对象包装                  │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│       解析层 (Parser Layer)              │
│  SQL 解析和字段识别                      │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│       配置层 (Config Layer)              │
│  表字段配置和缓存管理                    │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│       策略层 (Strategy Layer)             │
│  加密解密策略实现                        │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│      数据库层 (Database Layer)            │
│  MySQL / PostgreSQL / Oracle / ...      │
└─────────────────────────────────────────┘
```

---

## 核心组件

### 1. 拦截器组件（Interceptor）

#### SimpleInterceptorDriver

**职责**: JDBC 驱动拦截器，拦截数据库连接请求

**关键方法**:
- `connect(String url, Properties info)`: 建立连接并包装
- `acceptsURL(String url)`: 判断是否接受 URL
- `findUnderlyingDriver(String realUrl)`: 查找底层驱动

**设计要点**:
- URL 格式：`jdbc:interceptor:{真实数据库URL}`
- 提取真实 URL 并转发给底层驱动
- 包装返回的 Connection 对象

#### SimpleInterceptorConnection

**职责**: Connection 包装器，拦截 Statement 创建

**关键方法**:
- `createStatement()`: 返回 SimpleInterceptorStatement
- `prepareStatement(String sql)`: 返回 SimpleInterceptorPreparedStatement
- `prepareCall(String sql)`: 返回 SimpleInterceptorCallableStatement

#### SimpleInterceptorPreparedStatement

**职责**: PreparedStatement 包装器，处理参数加密

**关键方法**:
- `setString(int parameterIndex, String x)`: 拦截参数设置，加密字段值
- `executeQuery()`: 执行查询，返回包装的 ResultSet
- `executeUpdate()`: 执行更新

**加密流程**:
1. 解析 SQL 获取占位符映射
2. 识别需要加密的字段
3. 对参数值进行加密
4. 执行 SQL

#### ResultSetDecryptingProxy

**职责**: ResultSet 动态代理，处理查询结果解密

**关键方法**:
- `getString(String columnLabel)`: 拦截字段获取，解密返回值
- `getObject(int columnIndex)`: 拦截字段获取，解密返回值

**解密流程**:
1. 识别需要解密的字段
2. 获取加密值
3. 调用解密策略解密
4. 返回解密后的值

---

### 2. 解析器组件（Parser）

#### SecurtkitUtils

**职责**: SQL 解析工具类

**关键方法**:

```java
// 解析 SQL，返回占位符映射和加密字段信息
Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> 
    parseSql(String sql) throws JSQLParserException;

// 判断表是否需要加密
boolean needEncrypt(Collection<String> tables);
```

**解析流程**:
1. 将 SQL 中的 `?` 替换为自定义占位符
2. 使用 JSQLParser 解析 SQL
3. 使用访问者模式提取字段信息
4. 建立占位符到表字段的映射

#### PoJoEncrtptorStatementVisitor

**职责**: SQL 语句访问者，提取加密字段信息

**访问的模式**:
- `visit(Select)`: 处理 SELECT 语句
- `visit(Insert)`: 处理 INSERT 语句
- `visit(Update)`: 处理 UPDATE 语句

#### BaseFieldParseTable

**职责**: 字段解析基类，维护字段上下文

**数据结构**:
- `layerSelectTableFieldMap`: SELECT 中的字段
- `layerFieldTableMap`: FROM/JOIN 中的可用字段

---

### 3. 配置组件（Config）

#### FieldEncryptorProperties

**职责**: 字段加密配置属性类

**结构**:
```java
public class FieldEncryptorProperties {
    private boolean enable;
    private List<TableConfig> tables;
    
    public static class TableConfig {
        private String tableName;
        private List<FieldConfig> fields;
    }
    
    public static class FieldConfig {
        private String fieldName;
        private String strategy;  // 可选：自定义策略类名
    }
}
```

#### TableCache

**职责**: 表配置缓存，提供快速查找

**关键方法**:
```java
// 初始化缓存
static void init(FieldEncryptorProperties properties);

// 获取需要加密的表集合
static Set<String> getTables();

// 获取字段加密策略
static Class<? extends FieldEncryptorStrategy> 
    getTableFieldEncryptInfo(String tableName, String fieldName);
```

**缓存结构**:
- 表名 → 字段配置映射
- 字段名 → 加密策略映射

---

### 4. 策略组件（Strategy）

#### FieldEncryptorStrategy

**职责**: 加密解密策略接口

```java
public interface FieldEncryptorStrategy {
    String encryption(String fieldValue);
    String decryption(String fieldValue);
}
```

**实现要点**:
- 必须实现加密和解密方法
- 应该处理 null 值
- 应该处理异常情况

---

## 设计模式

### 1. 装饰器模式（Decorator Pattern）

**应用场景**: 包装 JDBC 对象

```java
// Connection 包装
SimpleInterceptorConnection wraps Connection

// PreparedStatement 包装
SimpleInterceptorPreparedStatement wraps PreparedStatement

// ResultSet 包装（动态代理）
ResultSetDecryptingProxy proxies ResultSet
```

**优势**:
- 不修改原有对象
- 可以多层包装
- 功能叠加

### 2. 代理模式（Proxy Pattern）

**应用场景**: JDBC 驱动代理

```java
SimpleInterceptorDriver proxies real JDBC Driver
```

**优势**:
- 拦截驱动调用
- 透明替换底层驱动

### 3. 访问者模式（Visitor Pattern）

**应用场景**: SQL 解析

```java
SQLStatement.accept(PoJoEncrtptorStatementVisitor)
```

**优势**:
- 分离数据结构与操作
- 易于扩展新的 SQL 类型支持

### 4. 策略模式（Strategy Pattern）

**应用场景**: 加密解密策略

```java
FieldEncryptorStrategy strategy = getStrategy(tableName, fieldName);
String encrypted = strategy.encryption(value);
```

**优势**:
- 支持多种加密算法
- 易于扩展新策略

### 5. 工厂模式（Factory Pattern）

**应用场景**: 创建加密策略实例

```java
FieldEncryptorStrategy strategy = StrategyFactory.create(strategyClassName);
```

---

## 数据流程

### 插入流程（INSERT）

```
[应用层]
User user = new User();
user.setName("张三");  // 明文
userMapper.insert(user);
         ↓
[MyBatis]
生成 PreparedStatement: INSERT INTO user (name) VALUES (?)
setString(1, "张三")
         ↓
[拦截层 - SimpleInterceptorPreparedStatement]
1. 解析 SQL: INSERT INTO user (name) VALUES (?)
2. 识别表: user
3. 识别字段: name (需要加密)
4. 加密: encryption("张三") → "加密后的值"
5. setString(1, "加密后的值")
         ↓
[数据库]
执行 INSERT，存储加密值
```

### 查询流程（SELECT）

```
[数据库]
返回 ResultSet:
  name = "加密后的值"
         ↓
[拦截层 - ResultSetDecryptingProxy]
1. 拦截 getString("name")
2. 识别字段: user.name (需要解密)
3. 获取加密值: "加密后的值"
4. 解密: decryption("加密后的值") → "张三"
5. 返回: "张三"
         ↓
[MyBatis]
映射到 User 对象
         ↓
[应用层]
user.getName() → "张三" (明文)
```

### 更新流程（UPDATE）

```
[应用层]
User user = userMapper.selectById(1L);  // 已解密
user.setName("新名字");
userMapper.updateById(user);
         ↓
[拦截层 - SimpleInterceptorPreparedStatement]
1. 解析 SQL: UPDATE user SET name = ?
2. 识别需要加密的字段: name
3. 加密新值: encryption("新名字")
4. 执行 UPDATE
         ↓
[数据库]
更新为加密值
```

### 多表查询流程（JOIN）

```
[SQL]
SELECT u.name, u.phone, o.customer_name 
FROM user u 
INNER JOIN orders o ON u.id = o.user_id
         ↓
[解析层]
1. 解析 SQL，识别表: user, orders
2. 识别字段:
   - user.name (需要解密)
   - user.phone (需要解密)
   - orders.customer_name (需要解密)
3. 建立字段映射
         ↓
[查询执行]
执行 JOIN 查询
         ↓
[结果集代理]
1. 拦截 getString("name") → 解密 user.name
2. 拦截 getString("phone") → 解密 user.phone
3. 拦截 getString("customer_name") → 解密 orders.customer_name
         ↓
[返回结果]
DTO 对象包含所有已解密的字段
```

---

## 扩展点

### 1. 自定义加密策略

实现 `FieldEncryptorStrategy` 接口：

```java
@Component
public class CustomEncryptorStrategy implements FieldEncryptorStrategy {
    @Override
    public String encryption(String fieldValue) {
        // 自定义加密逻辑
    }
    
    @Override
    public String decryption(String fieldValue) {
        // 自定义解密逻辑
    }
}
```

配置使用：

```yaml
securtkit:
  encryptor:
    tables:
      - table-name: user
        fields:
          - field-name: name
            strategy: com.example.CustomEncryptorStrategy
```

### 2. 自定义 SQL 解析器

扩展 `PoJoEncrtptorStatementVisitor`：

```java
public class CustomStatementVisitor extends PoJoEncrtptorStatementVisitor {
    @Override
    public void visit(Select select) {
        // 自定义 SELECT 解析逻辑
        super.visit(select);
    }
}
```

### 3. 自定义拦截器

扩展 `SimpleInterceptorPreparedStatement`：

```java
public class CustomPreparedStatement extends SimpleInterceptorPreparedStatement {
    public CustomPreparedStatement(PreparedStatement delegate, String sql) {
        super(delegate, sql);
        // 自定义初始化逻辑
    }
    
    @Override
    public void setString(int parameterIndex, String x) {
        // 自定义参数处理逻辑
        super.setString(parameterIndex, x);
    }
}
```

---

## 性能优化

### 1. SQL 解析缓存

**问题**: SQL 解析是 CPU 密集型操作

**优化方案**:
- 使用缓存存储解析结果
- 以 SQL 字符串的 MD5 作为缓存 key

```java
private static final Map<String, ParseResult> SQL_CACHE = new ConcurrentHashMap<>();

public Pair<...> parseSql(String sql) {
    String cacheKey = md5(sql);
    ParseResult cached = SQL_CACHE.get(cacheKey);
    if (cached != null) {
        return cached;
    }
    // 解析并缓存
}
```

### 2. 配置缓存

**问题**: 频繁查找表配置

**优化方案**:
- TableCache 提供内存缓存
- 表名 → 字段配置的快速查找

### 3. 加密解密优化

**问题**: 加密解密是 CPU 密集型操作

**优化方案**:
- 使用线程安全的加密实现
- 考虑使用硬件加速（如果可用）
- 批量操作时可以考虑并行处理

### 4. 连接池优化

**问题**: 连接创建开销

**优化方案**:
- 使用连接池（HikariCP、Druid 等）
- 合理配置连接池大小

---

## 安全考虑

### 1. 密钥管理

- **不要在代码中硬编码密钥**
- **使用密钥管理服务**（AWS KMS、HashiCorp Vault）
- **支持密钥轮换**

### 2. 加密算法

- **使用标准加密算法**（AES-256）
- **避免使用弱加密算法**（DES、RC4）
- **使用适当的加密模式**（CBC、GCM）

### 3. 日志安全

- **避免在日志中输出敏感数据**
- **加密的字段值不应该出现在日志中**
- **使用日志脱敏工具**

### 4. 错误处理

- **不要泄露密钥信息**
- **错误信息不应该包含敏感数据**
- **实现安全的降级策略**

---

## 扩展计划

### 短期（v1.1）

- [ ] 支持更多 SQL 语句类型
- [ ] 性能监控和指标收集
- [ ] 更完善的错误处理

### 中期（v1.2）

- [ ] 支持字段级别的权限控制
- [ ] 审计日志功能
- [ ] 密钥自动轮换

### 长期（v2.0）

- [ ] 支持分布式场景
- [ ] 更高级的加密算法支持
- [ ] 可视化配置界面

---

## 技术栈

### 核心依赖

- **Java**: 1.8+
- **JSQLParser**: 4.9 (SQL 解析)
- **Lombok**: 1.18.36 (代码简化)
- **SLF4J**: 1.7.30 (日志)

### Spring Boot 支持

- **Spring Boot**: 2.6.13+
- **Spring Boot Auto Configuration**: 自动配置支持

---

## 参考资料

- [JDBC 规范](https://docs.oracle.com/javase/tutorial/jdbc/)
- [JSQLParser 文档](https://github.com/JSQLParser/JSqlParser)
- [P6Spy 项目](https://github.com/p6spy/p6spy)

---

**文档版本**: v1.0  
**最后更新**: 2025-01-XX

