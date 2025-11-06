# 代码拆分优化计划

## 概述

本文档详细说明 `TableCache` 和 `SimpleInterceptorPreparedStatement` 两个核心类的拆分优化计划，旨在降低代码复杂度，提高可维护性和可测试性。

---

## 一、TableCache 拆分优化计划

### 1.1 当前问题分析

#### 1.1.1 代码规模
- **总行数**：639 行
- **方法数**：17 个（8 个 public，9 个 private）
- **静态字段**：6 个
- **职责数量**：5+ 个

#### 1.1.2 职责过多（违反单一职责原则）
`TableCache` 当前承担了以下职责：
1. **配置验证**：验证表名、字段名格式，验证策略类
2. **配置初始化**：初始化配置管理器，初始化各数据源配置
3. **缓存管理**：管理单数据源和多数据源的缓存
4. **配置查询**：提供表配置、字段配置的查询接口
5. **表名处理**：提取纯表名（去掉 schema、数据库名、双引号）
6. **SQL 解析缓存初始化**：初始化 SQL 解析缓存配置
7. **异常处理初始化**：初始化异常处理策略

#### 1.1.3 复杂度问题
- **方法过长**：`init()` 方法 64 行，`validateConfiguration()` 方法 87 行，`initForDatasource()` 方法 80 行
- **嵌套层级深**：`validateConfiguration()` 方法中有 3 层嵌套循环
- **静态状态过多**：6 个静态字段，难以测试和维护

### 1.2 拆分方案

#### 1.2.1 拆分目标
将 `TableCache` 拆分为以下 3 个类：

1. **`ConfigInitializer`**（配置初始化器）
   - 职责：配置验证、配置初始化、SQL 解析缓存初始化、异常处理初始化
   - 位置：`io.github.hexlodev.core.config.ConfigInitializer`

2. **`TableConfigRegistry`**（表配置注册表）
   - 职责：表配置的存储、查询、表名处理
   - 位置：`io.github.hexlodev.core.config.TableConfigRegistry`

3. **`TableCache`**（表缓存门面）
   - 职责：提供统一的对外接口，委托给 `TableConfigRegistry`
   - 位置：`io.github.hexlodev.core.TableCache`（保持不变）

#### 1.2.2 类设计详情

##### ConfigInitializer（配置初始化器）

**职责**：
- 验证配置有效性（表名、字段名格式，策略类）
- 初始化配置管理器
- 初始化各数据源配置
- 初始化 SQL 解析缓存
- 初始化异常处理策略

**方法列表**：
```java
public class ConfigInitializer {
    // 初始化配置
    public static void initialize(FieldEncryptorProperties properties);
    
    // 验证配置
    private static void validateConfiguration(FieldEncryptorProperties properties);
    
    // 判断是否多数据源配置
    private static boolean isMultiDatasourceConfig(FieldEncryptorProperties properties);
    
    // 为指定数据源初始化配置
    private static void initForDatasource(String datasourceId, DataSourceConfigManager configManager);
    
    // 初始化 SQL 解析缓存
    private static void initSqlParseCache(DataSourceConfigManager.MergedConfig mergedConfig);
    
    // 提取纯表名（工具方法）
    public static String extractPureTableName(String tableName);
}
```

**字段**：
- `private static final AtomicBoolean INITIALIZED` - 初始化状态
- `private static DataSourceConfigManager configManager` - 配置管理器

##### TableConfigRegistry（表配置注册表）

**职责**：
- 存储表配置缓存（单数据源和多数据源）
- 提供表配置查询接口
- 提供表名处理工具方法

**方法列表**：
```java
public class TableConfigRegistry {
    // 注册表配置（单数据源）
    public static void registerTable(String tableName, Map<String, Class<? extends FieldEncryptorStrategy>> fieldMap);
    
    // 注册表配置（多数据源）
    public static void registerTable(String datasourceId, String tableName, Map<String, Class<? extends FieldEncryptorStrategy>> fieldMap);
    
    // 检查表是否需要加密
    public static boolean isTableEncrypted(String tableName);
    public static boolean isTableEncrypted(String tableName, String datasourceId);
    
    // 获取表字段配置
    public static Map<String, Class<? extends FieldEncryptorStrategy>> getTableFields(String tableName);
    public static Map<String, Class<? extends FieldEncryptorStrategy>> getTableFields(String tableName, String datasourceId);
    
    // 获取表字段名列表
    public static List<String> getTableFieldNames(String tableName);
    
    // 获取字段加密策略
    public static Class<? extends FieldEncryptorStrategy> getFieldStrategy(String tableName, String fieldName);
    public static Class<? extends FieldEncryptorStrategy> getFieldStrategy(String tableName, String fieldName, String datasourceId);
    
    // 获取所有加密表名
    public static Set<String> getEncryptedTables();
    public static Set<String> getEncryptedTables(String datasourceId);
    
    // 清空缓存（用于测试）
    public static void clear();
}
```

**字段**：
- `TABLE_FIELD_ENCRYPT_INFO` - 单数据源缓存
- `FIELD_ENCRYPT_TABLE` - 单数据源表集合
- `DATASOURCE_TABLE_FIELD_ENCRYPT_INFO` - 多数据源缓存
- `DATASOURCE_FIELD_ENCRYPT_TABLE` - 多数据源表集合

##### TableCache（表缓存门面）

**职责**：
- 提供向后兼容的静态接口
- 委托给 `TableConfigRegistry` 和 `ConfigInitializer`

**方法列表**：
```java
public class TableCache {
    // 初始化（委托给 ConfigInitializer）
    public static void init(FieldEncryptorProperties properties);
    
    // 检查表是否需要加密（委托给 TableConfigRegistry）
    public static boolean concatTable(String tableName);
    public static boolean concatTable(String tableName, String datasourceId);
    
    // 获取表字段配置（委托给 TableConfigRegistry）
    public static Map<String, Class<? extends FieldEncryptorStrategy>> getTableFieldEncryptInfo(String tableName);
    public static Map<String, Class<? extends FieldEncryptorStrategy>> getTableFieldEncryptInfo(String tableName, String datasourceId);
    
    // 获取表字段名列表（委托给 TableConfigRegistry）
    public static List<String> getTableFieldName(String tableName);
    
    // 获取字段加密策略（委托给 TableConfigRegistry）
    public static Class<? extends FieldEncryptorStrategy> getTableFieldEncryptStrategy(String tableName, String fieldName);
    public static Class<? extends FieldEncryptorStrategy> getTableFieldEncryptStrategy(String tableName, String fieldName, String datasourceId);
    
    // 获取所有加密表（委托给 TableConfigRegistry）
    public static Set<String> getTables();
    
    // 检查是否已初始化（委托给 ConfigInitializer）
    public static boolean isInit();
    
    // 重置（委托给 ConfigInitializer 和 TableConfigRegistry）
    public static void reset();
}
```

### 1.3 实施步骤

#### 阶段 1：创建新类（不破坏现有功能）
1. ✅ 创建 `ConfigInitializer` 类
   - 从 `TableCache` 中提取配置验证和初始化相关方法
   - 保持方法签名和逻辑不变

2. ✅ 创建 `TableConfigRegistry` 类
   - 从 `TableCache` 中提取缓存存储和查询相关方法
   - 保持方法签名和逻辑不变

#### 阶段 2：重构 TableCache（委托模式）
3. ✅ 重构 `TableCache.init()`
   - 委托给 `ConfigInitializer.initialize()`

4. ✅ 重构 `TableCache` 的查询方法
   - 委托给 `TableConfigRegistry` 的对应方法

#### 阶段 3：更新引用
5. ✅ 检查并更新所有引用 `TableCache` 的代码
   - 确保所有调用都通过 `TableCache` 门面类

#### 阶段 4：测试验证
6. ✅ 运行所有测试，确保功能正常
7. ✅ 代码审查，确保拆分后的代码更清晰

### 1.4 预期效果

- **代码行数**：`TableCache` 从 639 行减少到约 150 行（减少 77%）
- **方法数**：每个类的方法数控制在 10 个以内
- **职责清晰**：每个类只负责一个明确的职责
- **可测试性**：可以独立测试配置初始化和配置查询
- **可维护性**：修改配置验证逻辑不影响查询逻辑

---

## 二、SimpleInterceptorPreparedStatement 拆分优化计划

### 2.1 当前问题分析

#### 2.1.1 代码规模
- **总行数**：1466 行
- **方法数**：100+ 个（实现 PreparedStatement 接口的所有方法）
- **职责数量**：4+ 个

#### 2.1.2 职责过多（违反单一职责原则）
`SimpleInterceptorPreparedStatement` 当前承担了以下职责：
1. **参数加密**：拦截所有 `setXxx()` 方法，对需要加密的参数进行加密
2. **SQL 解析**：解析 SQL 语句，识别需要加密的表和字段
3. **SQL 执行**：执行 SQL 并记录日志和性能指标
4. **结果集包装**：包装 ResultSet 以实现自动解密
5. **参数值缓存**：缓存参数值用于日志输出
6. **SQL 格式化**：生成最终执行的 SQL（用于日志）

#### 2.1.3 复杂度问题
- **方法过多**：100+ 个方法，大部分是 JDBC 接口的委托方法
- **参数加密逻辑重复**：多个 `setXxx()` 方法中都有相似的加密逻辑
- **SQL 执行逻辑重复**：`executeQuery()`、`executeUpdate()`、`execute()` 中都有相似的日志和性能记录逻辑

### 2.2 拆分方案

#### 2.2.1 拆分目标
将 `SimpleInterceptorPreparedStatement` 拆分为以下 3 个类：

1. **`ParameterEncryptor`**（参数加密器）
   - 职责：参数加密逻辑的统一处理
   - 位置：`io.github.hexlodev.core.interceptor.ParameterEncryptor`

2. **`SqlExecutor`**（SQL 执行器）
   - 职责：SQL 执行、日志记录、性能监控
   - 位置：`io.github.hexlodev.core.interceptor.SqlExecutor`

3. **`SimpleInterceptorPreparedStatement`**（PreparedStatement 拦截器）
   - 职责：实现 PreparedStatement 接口，委托给 `ParameterEncryptor` 和 `SqlExecutor`
   - 位置：`io.github.hexlodev.core.interceptor.SimpleInterceptorPreparedStatement`（保持不变）

#### 2.2.2 类设计详情

##### ParameterEncryptor（参数加密器）

**职责**：
- 判断参数是否需要加密
- 执行参数加密
- 处理加密异常

**方法列表**：
```java
public class ParameterEncryptor {
    // 构造函数
    public ParameterEncryptor(String datasourceId, Set<String> tables, 
                              Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> sqlParseResult);
    
    // 加密字符串参数
    public String encryptString(int parameterIndex, String value);
    
    // 加密对象参数（通用方法）
    public Object encryptObject(int parameterIndex, Object value);
    
    // 加密 Reader 参数
    public String encryptReader(int parameterIndex, java.io.Reader reader, long length);
    
    // 加密 Clob 参数
    public String encryptClob(int parameterIndex, Clob clob);
    
    // 加密 NClob 参数
    public String encryptNClob(int parameterIndex, NClob nClob);
    
    // 判断参数是否需要加密
    public boolean needEncrypt(int parameterIndex);
    
    // 获取参数对应的字段信息
    public ColumnTableDto getFieldInfo(int parameterIndex);
}
```

**字段**：
- `datasourceId` - 数据源标识
- `tables` - SQL 中涉及的表
- `sqlParseResult` - SQL 解析结果
- `parameterIndexToFieldMap` - 参数索引到字段信息的映射

##### SqlExecutor（SQL 执行器）

**职责**：
- 执行 SQL 查询
- 执行 SQL 更新
- 记录 SQL 日志
- 记录性能指标
- 包装 ResultSet 以实现解密

**方法列表**：
```java
public class SqlExecutor {
    // 构造函数
    public SqlExecutor(PreparedStatement delegate, String sql, String datasourceId, 
                      Set<String> tables, Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> sqlParseResult);
    
    // 执行查询
    public ResultSet executeQuery() throws SQLException;
    
    // 执行更新
    public int executeUpdate() throws SQLException;
    
    // 执行 SQL（通用）
    public boolean execute() throws SQLException;
    
    // 生成最终 SQL（用于日志）
    public String buildFinalSql(Map<Integer, Object> parameterValues);
    
    // 格式化 SQL 值
    private String formatSqlValue(Object value);
    
    // 记录 SQL 日志
    private void logSql(String sqlType, String sql, long executionTime, Object result);
    
    // 记录 SQL 错误日志
    private void logSqlError(String sqlType, String sql, long executionTime, SQLException e);
}
```

**字段**：
- `delegate` - 真实的 PreparedStatement
- `sql` - 原始 SQL
- `datasourceId` - 数据源标识
- `tables` - SQL 中涉及的表
- `sqlParseResult` - SQL 解析结果

##### SimpleInterceptorPreparedStatement（PreparedStatement 拦截器）

**职责**：
- 实现 PreparedStatement 接口的所有方法
- 委托参数加密给 `ParameterEncryptor`
- 委托 SQL 执行给 `SqlExecutor`
- 管理参数值缓存（用于日志）

**方法列表**：
```java
public class SimpleInterceptorPreparedStatement implements PreparedStatement {
    // 构造函数
    public SimpleInterceptorPreparedStatement(PreparedStatement delegate, String sql);
    public SimpleInterceptorPreparedStatement(PreparedStatement delegate, String sql, String datasourceId);
    
    // 参数设置方法（委托给 ParameterEncryptor）
    @Override
    public void setString(int parameterIndex, String x) throws SQLException;
    
    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException;
    
    // ... 其他 setXxx() 方法
    
    // SQL 执行方法（委托给 SqlExecutor）
    @Override
    public ResultSet executeQuery() throws SQLException;
    
    @Override
    public int executeUpdate() throws SQLException;
    
    @Override
    public boolean execute() throws SQLException;
    
    // 其他 PreparedStatement 接口方法（直接委托）
    // ...
}
```

**字段**：
- `delegate` - 真实的 PreparedStatement
- `sql` - 原始 SQL
- `datasourceId` - 数据源标识
- `tables` - SQL 中涉及的表
- `sqlParseResult` - SQL 解析结果
- `parameterValues` - 参数值缓存
- `parameterEncryptor` - 参数加密器
- `sqlExecutor` - SQL 执行器

### 2.3 实施步骤

#### 阶段 1：创建新类（不破坏现有功能）
1. ✅ 创建 `ParameterEncryptor` 类
   - 从 `SimpleInterceptorPreparedStatement` 中提取参数加密相关逻辑
   - 提取 `maybeEncryptValue()`、`buildParameterIndexMap()` 等方法

2. ✅ 创建 `SqlExecutor` 类
   - 从 `SimpleInterceptorPreparedStatement` 中提取 SQL 执行相关逻辑
   - 提取 `executeQuery()`、`executeUpdate()`、`execute()`、`buildFinalSql()` 等方法

#### 阶段 2：重构 SimpleInterceptorPreparedStatement（委托模式）
3. ✅ 重构构造函数
   - 创建 `ParameterEncryptor` 和 `SqlExecutor` 实例

4. ✅ 重构参数设置方法
   - 委托给 `ParameterEncryptor.encryptXxx()` 方法
   - 简化代码，消除重复逻辑

5. ✅ 重构 SQL 执行方法
   - 委托给 `SqlExecutor.executeXxx()` 方法
   - 简化代码，消除重复逻辑

#### 阶段 3：更新引用
6. ✅ 检查并更新所有引用 `SimpleInterceptorPreparedStatement` 的代码
   - 确保所有调用都正常工作

#### 阶段 4：测试验证
7. ✅ 运行所有测试，确保功能正常
8. ✅ 代码审查，确保拆分后的代码更清晰

### 2.4 预期效果

- **代码行数**：`SimpleInterceptorPreparedStatement` 从 1466 行减少到约 400 行（减少 73%）
- **方法数**：每个类的方法数控制在合理范围内
- **职责清晰**：参数加密、SQL 执行、接口实现职责分离
- **可测试性**：可以独立测试参数加密和 SQL 执行逻辑
- **可维护性**：修改加密逻辑不影响执行逻辑，修改执行逻辑不影响接口实现
- **代码复用**：消除重复的参数加密逻辑和 SQL 执行逻辑

---

## 三、拆分后的依赖关系

### 3.1 TableCache 拆分后的依赖关系

```
TableCache (门面)
    ├── ConfigInitializer
    │   ├── DataSourceConfigManager
    │   ├── FieldEncryptorProperties
    │   ├── SqlParseCache
    │   └── EncryptionHandler
    └── TableConfigRegistry
        └── FieldEncryptorStrategy
```

### 3.2 SimpleInterceptorPreparedStatement 拆分后的依赖关系

```
SimpleInterceptorPreparedStatement (拦截器)
    ├── ParameterEncryptor
    │   ├── TableCache
    │   ├── StrategyCache
    │   ├── EncryptionHandler
    │   └── SecurtkitUtils
    └── SqlExecutor
        ├── ResultSetDecryptingProxy
        └── SecurtkitUtils
```

---

## 四、实施优先级

### 4.1 优先级排序

1. **高优先级**：拆分 `TableCache`
   - 影响范围：配置管理相关代码
   - 风险：中等（需要仔细处理向后兼容）
   - 收益：高（代码复杂度显著降低）

2. **高优先级**：拆分 `SimpleInterceptorPreparedStatement`
   - 影响范围：SQL 执行相关代码
   - 风险：中等（需要仔细处理 JDBC 接口实现）
   - 收益：高（代码复杂度显著降低，可测试性提升）

### 4.2 实施顺序

1. 先拆分 `TableCache`（相对独立，影响范围较小）
2. 再拆分 `SimpleInterceptorPreparedStatement`（依赖 `TableCache`，拆分后可以更好地使用新的接口）

---

## 五、风险评估与应对

### 5.1 风险点

1. **向后兼容性风险**
   - **风险**：拆分后可能破坏现有代码的调用
   - **应对**：保持 `TableCache` 和 `SimpleInterceptorPreparedStatement` 的公共接口不变，使用委托模式

2. **测试覆盖风险**
   - **风险**：拆分后可能出现测试遗漏
   - **应对**：运行完整的测试套件，确保所有测试通过

3. **性能影响风险**
   - **风险**：拆分后可能增加方法调用开销
   - **应对**：性能影响可忽略（方法调用开销很小），如有必要可以进行性能测试

### 5.2 回滚方案

如果拆分后出现问题，可以：
1. 保留原代码备份
2. 使用 Git 分支进行拆分，确保可以快速回滚
3. 分阶段实施，每个阶段完成后进行充分测试

---

## 六、验收标准

### 6.1 功能验收

- ✅ 所有现有功能正常工作
- ✅ 所有测试用例通过
- ✅ 性能无明显下降（< 5%）

### 6.2 代码质量验收

- ✅ 每个类的代码行数 < 500 行
- ✅ 每个类的方法数 < 20 个
- ✅ 每个方法的代码行数 < 50 行
- ✅ 圈复杂度 < 10

### 6.3 可维护性验收

- ✅ 职责清晰，每个类只负责一个明确的职责
- ✅ 代码可读性提升，注释完整
- ✅ 可以独立测试各个组件

---

## 七、时间估算

### 7.1 TableCache 拆分

- **阶段 1**：创建新类（2-3 小时）
- **阶段 2**：重构 TableCache（1-2 小时）
- **阶段 3**：更新引用（1 小时）
- **阶段 4**：测试验证（1-2 小时）
- **总计**：5-8 小时

### 7.2 SimpleInterceptorPreparedStatement 拆分

- **阶段 1**：创建新类（3-4 小时）
- **阶段 2**：重构 SimpleInterceptorPreparedStatement（2-3 小时）
- **阶段 3**：更新引用（1 小时）
- **阶段 4**：测试验证（2-3 小时）
- **总计**：8-11 小时

### 7.3 总计

- **总时间**：13-19 小时（约 2-3 个工作日）

---

## 八、后续优化建议

### 8.1 进一步优化方向

1. **引入依赖注入**：将静态方法改为实例方法，使用依赖注入框架管理
2. **提取接口**：为 `ParameterEncryptor` 和 `SqlExecutor` 提取接口，提高可测试性
3. **策略模式**：将参数加密逻辑进一步抽象为策略模式
4. **观察者模式**：将 SQL 日志记录改为观察者模式，支持多种日志输出

### 8.2 长期重构目标

- 减少静态状态的使用
- 提高代码的可测试性
- 降低类之间的耦合度
- 提高代码的可扩展性

---

## 附录：相关文件清单

### A.1 TableCache 相关文件

- `securt-kit-core/src/main/java/io/github/hexlodev/core/TableCache.java`（现有）
- `securt-kit-core/src/main/java/io/github/hexlodev/core/config/ConfigInitializer.java`（新建）
- `securt-kit-core/src/main/java/io/github/hexlodev/core/config/TableConfigRegistry.java`（新建）

### A.2 SimpleInterceptorPreparedStatement 相关文件

- `securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/SimpleInterceptorPreparedStatement.java`（现有）
- `securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/ParameterEncryptor.java`（新建）
- `securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/SqlExecutor.java`（新建）

---

**文档版本**：v1.0  
**创建日期**：2025-01-XX  
**最后更新**：2025-01-XX

