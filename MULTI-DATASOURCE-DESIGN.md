# 多数据源支持设计方案

## 1. 现状分析

### 1.1 当前架构特点

当前项目采用 JDBC 驱动拦截方式实现字段加密/解密：

1. **驱动拦截层**：`SimpleInterceptorDriver` 作为 JDBC 驱动拦截器
2. **连接包装层**：`SimpleInterceptorConnection` 包装真实连接
3. **语句拦截层**：`SimpleInterceptorPreparedStatement` 拦截 SQL 执行
4. **全局配置缓存**：`TableCache` 使用静态 Map 存储所有表的加密配置
5. **配置初始化**：在驱动静态初始化块中从 Spring 容器获取配置

### 1.2 多数据源场景下的问题

#### 问题1：配置隔离缺失
- **现状**：`TableCache` 是全局静态缓存，所有数据源共享同一套配置
- **影响**：无法为不同数据源配置不同的加密策略
- **示例场景**：
  ```yaml
  # 主库：MySQL，需要加密用户表的手机号
  datasource:
    primary:
      url: jdbc:interceptor:mysql://localhost:3306/main_db
  
  # 从库：MySQL，不需要加密
  datasource:
    secondary:
      url: jdbc:interceptor:mysql://localhost:3306/read_db
  
  # 第三方库：PostgreSQL，需要不同的加密策略
  datasource:
    third:
      url: jdbc:interceptor:postgresql://localhost:5432/external_db
  ```

#### 问题2：数据源标识缺失
- **现状**：连接创建时无法识别属于哪个数据源
- **影响**：无法根据数据源选择对应的配置
- **代码位置**：`SimpleInterceptorDriver.connect()` 方法中

#### 问题3：初始化时机问题
- **现状**：驱动静态初始化块中只初始化一次，使用 Spring 容器中的唯一配置
- **影响**：多数据源场景下，每个数据源可能有不同的配置，但无法区分
- **代码位置**：`SimpleInterceptorDriver` 静态块

#### 问题4：表名冲突问题
- **现状**：不同数据源可能有相同表名，但需要不同的加密策略
- **影响**：配置冲突，无法区分
- **示例**：
  ```yaml
  # 主库的 user 表需要加密 name 和 phone
  securtkit:
    encryptor:
      tables:
        - table-name: user
          fields:
            - field-name: name
            - field-name: phone
  
  # 从库的 user 表不需要加密（或者需要不同的策略）
  ```

## 2. 设计方案

### 2.1 设计目标

1. **配置隔离**：支持为每个数据源配置独立的加密策略
2. **向后兼容**：保持单数据源场景下的现有配置方式
3. **灵活扩展**：支持数据源级别的配置覆盖
4. **性能优化**：最小化配置查找的性能开销

### 2.2 核心设计思路

#### 方案A：数据源标识 + 配置分层（推荐）

**核心思想**：
- 为每个数据源添加唯一标识（DataSource ID）
- 配置结构：全局配置 + 数据源级别配置（可覆盖）
- 连接创建时绑定数据源标识
- 执行 SQL 时根据数据源标识查找配置

**架构图**：
```
┌─────────────────────────────────────────────────────────┐
│                  应用层 (Application)                    │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│              SimpleInterceptorDriver                     │
│  - 识别数据源标识（从 URL 或连接属性）                    │
│  - 创建连接时绑定数据源标识                               │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│         SimpleInterceptorConnection (带数据源标识)        │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│      SimpleInterceptorPreparedStatement                  │
│  - 根据数据源标识查找配置                                  │
│  - 使用对应数据源的加密策略                                │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│              DataSourceConfigManager                      │
│  - 管理所有数据源的配置                                    │
│  - 提供配置查找接口                                        │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│         配置存储结构 (分层)                                │
│  ┌─────────────────────────────────────────┐            │
│  │ 全局配置 (Global Config)                 │            │
│  │ - 默认加密策略                           │            │
│  │ - 全局表配置                             │            │
│  └─────────────────────────────────────────┘            │
│  ┌─────────────────────────────────────────┐            │
│  │ 数据源1配置 (datasource1)                │            │
│  │ - 继承全局配置                           │            │
│  │ - 覆盖特定表配置                         │            │
│  └─────────────────────────────────────────┘            │
│  ┌─────────────────────────────────────────┐            │
│  │ 数据源2配置 (datasource2)                │            │
│  │ - 继承全局配置                           │            │
│  │ - 覆盖特定表配置                         │            │
│  └─────────────────────────────────────────┘            │
└─────────────────────────────────────────────────────────┘
```

### 2.3 详细设计方案

#### 2.3.1 数据源标识机制

**方案1：URL 参数方式（推荐）**
```java
// URL 格式：jdbc:interceptor:mysql://localhost:3306/db?datasource-id=primary
// 优点：简单直接，易于理解
// 缺点：需要修改 URL

// 解析示例
String url = "jdbc:interceptor:mysql://localhost:3306/db?datasource-id=primary";
String datasourceId = extractDatasourceId(url); // "primary"
```

**方案2：连接属性方式**
```java
// 通过 Properties 传递数据源标识
Properties props = new Properties();
props.setProperty("datasource-id", "primary");
Connection conn = DriverManager.getConnection(url, props);

// 优点：不修改 URL，更灵活
// 缺点：需要修改连接创建代码
```

**方案3：Spring Bean 名称方式**
```java
// 通过数据源 Bean 名称自动识别
@Bean("primaryDataSource")
public DataSource primaryDataSource() {
    // Spring 容器中可以通过 Bean 名称识别
}

// 优点：与 Spring 集成好
// 缺点：需要额外机制传递 Bean 名称
```

**推荐方案：混合方式**
- 优先使用 URL 参数：`jdbc:interceptor:mysql://...?datasource-id=primary`
- 其次使用连接属性：`Properties.setProperty("datasource-id", "primary")`
- 最后使用默认值：`"default"`（向后兼容）

#### 2.3.2 配置结构设计

**配置文件结构（YAML）**：
```yaml
securtkit:
  encryptor:
    # 全局配置（所有数据源共享）
    enable: true
    failure-policy: FALLBACK
    sql-parse-cache:
      enable: true
      max-size: 1000
    
    # 表配置（通过 datasource-id 区分不同数据源）
    tables:
      # 主数据源配置
      - table-name: user
        datasource-id: primary
        fields:
          - field-name: name
            strategy: com.example.AESStrategy
          - field-name: phone
            strategy: com.example.AESStrategy
      
      # 从数据源配置（不配置等于关闭加密）
      # secondary 数据源不配置，等于关闭加密
      
      # 第三方数据源配置（使用不同的加密策略）
      - table-name: user
        datasource-id: third
        fields:
          - field-name: name
            strategy: com.example.RSAStrategy
```

**配置类结构**：
```java
@Data
@ConfigurationProperties(prefix = "securtkit.encryptor")
public class FieldEncryptorProperties {
    
    /**
     * 是否启用加密（全局配置，所有数据源共享）
     */
    private boolean enable;
    
    /**
     * 失败处理策略（全局配置，所有数据源共享）
     */
    private FailurePolicy failurePolicy;
    
    /**
     * SQL 解析缓存配置（全局配置，所有数据源共享）
     */
    private SqlParseCacheConfig sqlParseCache;
    
    /**
     * 表配置列表（通过 datasource-id 区分不同数据源）
     */
    private List<TableConfig> tables;
    
    /**
     * 表配置
     */
    @Data
    public static class TableConfig {
        /**
         * 表名
         */
        private String tableName;
        
        /**
         * 数据源标识（可选）
         * - 不指定：应用到所有数据源（单数据源场景）
         * - 指定：只应用到该数据源（多数据源场景）
         */
        private String datasourceId;
        
        /**
         * 字段配置列表
         */
        private List<FieldConfig> fields;
    }
}
```

#### 2.3.3 配置管理器设计

**核心类：DataSourceConfigManager**

```java
/**
 * 数据源配置管理器
 * 
 * 职责：
 * 1. 管理所有数据源的配置
 * 2. 提供配置查找接口（根据数据源标识）
 * 3. 处理配置继承和覆盖逻辑
 */
public class DataSourceConfigManager {
    
    /**
     * 全局配置
     */
    private final FieldEncryptorProperties.GlobalConfig globalConfig;
    
    /**
     * 数据源配置映射
     * Key: 数据源标识
     * Value: 数据源配置
     */
    private final Map<String, FieldEncryptorProperties.DataSourceConfig> datasourceConfigs;
    
    /**
     * 数据源配置缓存（合并后的最终配置）
     * Key: 数据源标识
     * Value: 合并后的配置
     */
    private final Map<String, MergedConfig> mergedConfigCache = new ConcurrentHashMap<>();
    
    /**
     * 初始化配置管理器
     */
    public static void init(FieldEncryptorProperties properties) {
        // 解析配置文件，初始化全局配置和数据源配置
    }
    
    /**
     * 根据数据源标识获取配置
     * 
     * @param datasourceId 数据源标识，如果为 null 则返回全局配置
     * @return 合并后的配置
     */
    public MergedConfig getConfig(String datasourceId) {
        // 1. 如果数据源标识为空，返回全局配置
        if (StrUtil.isBlank(datasourceId) || "default".equals(datasourceId)) {
            return getGlobalMergedConfig();
        }
        
        // 2. 从缓存中获取
        return mergedConfigCache.computeIfAbsent(datasourceId, this::mergeConfig);
    }
    
    /**
     * 合并配置（数据源配置覆盖全局配置）
     */
    private MergedConfig mergeConfig(String datasourceId) {
        FieldEncryptorProperties.DataSourceConfig dsConfig = datasourceConfigs.get(datasourceId);
        
        if (dsConfig == null) {
            // 如果没有数据源级别配置，返回全局配置
            return getGlobalMergedConfig();
        }
        
        // 合并逻辑：
        // 1. enable: 数据源配置优先，如果为 null 则继承全局
        // 2. failurePolicy: 数据源配置优先，如果为 null 则继承全局
        // 3. tables: 数据源配置覆盖全局配置（表名匹配）
        // 4. sqlParseCache: 数据源配置优先，如果为 null 则继承全局
        
        MergedConfig merged = new MergedConfig();
        merged.setEnable(dsConfig.getEnable() != null ? dsConfig.getEnable() : globalConfig.getEnable());
        merged.setFailurePolicy(dsConfig.getFailurePolicy() != null ? 
            dsConfig.getFailurePolicy() : globalConfig.getFailurePolicy());
        merged.setSqlParseCache(dsConfig.getSqlParseCache() != null ? 
            dsConfig.getSqlParseCache() : globalConfig.getSqlParseCache());
        
        // 合并表配置：数据源配置覆盖全局配置
        Map<String, TableConfig> tableMap = new HashMap<>();
        if (globalConfig.getTables() != null) {
            for (TableConfig table : globalConfig.getTables()) {
                tableMap.put(table.getTableName().toLowerCase(), table);
            }
        }
        if (dsConfig.getTables() != null) {
            for (TableConfig table : dsConfig.getTables()) {
                tableMap.put(table.getTableName().toLowerCase(), table);
            }
        }
        merged.setTables(new ArrayList<>(tableMap.values()));
        
        return merged;
    }
}
```

#### 2.3.4 TableCache 改造

**方案：支持数据源级别的配置缓存**

```java
public class TableCache {
    
    /**
     * 数据源配置管理器
     */
    private static DataSourceConfigManager configManager;
    
    /**
     * 数据源级别的表字段加密信息缓存
     * Key: 数据源标识
     * Value: Map<表名, Map<字段名, 策略类>>
     */
    private static final Map<String, Map<String, Map<String, Class<? extends FieldEncryptorStrategy>>>> 
        DATASOURCE_TABLE_FIELD_ENCRYPT_INFO = new ConcurrentHashMap<>();
    
    /**
     * 数据源级别的加密表集合
     * Key: 数据源标识
     * Value: Set<表名>
     */
    private static final Map<String, Set<String>> DATASOURCE_FIELD_ENCRYPT_TABLE = new ConcurrentHashMap<>();
    
    /**
     * 初始化（支持多数据源）
     */
    public static void init(FieldEncryptorProperties properties) {
        // 1. 初始化配置管理器
        configManager = new DataSourceConfigManager(properties);
        
        // 2. 为每个数据源初始化 TableCache
        if (properties.getDatasources() != null) {
            for (String datasourceId : properties.getDatasources().keySet()) {
                initForDatasource(datasourceId);
            }
        }
        
        // 3. 初始化全局配置（向后兼容）
        initForDatasource("default");
    }
    
    /**
     * 为指定数据源初始化配置
     */
    private static void initForDatasource(String datasourceId) {
        MergedConfig config = configManager.getConfig(datasourceId);
        if (config == null || !config.isEnable()) {
            return;
        }
        
        // 解析表配置，存储到缓存中
        Map<String, Map<String, Class<? extends FieldEncryptorStrategy>>> tableFieldMap = new HashMap<>();
        Set<String> encryptTables = new HashSet<>();
        
        if (config.getTables() != null) {
            for (TableConfig table : config.getTables()) {
                String tableName = table.getTableName().toLowerCase();
                Map<String, Class<? extends FieldEncryptorStrategy>> fieldMap = new HashMap<>();
                
                if (table.getFields() != null) {
                    for (FieldConfig field : table.getFields()) {
                        String fieldName = field.getFieldName().toLowerCase();
                        String strategy = field.getStrategy();
                        if (StrUtil.isNotBlank(strategy)) {
                            fieldMap.put(fieldName, ClassUtil.loadClass(strategy));
                        } else {
                            // 使用默认策略
                            FieldEncryptorStrategy defaultStrategy = StrategyCache.getStrategy(FieldEncryptorStrategy.class);
                            fieldMap.put(fieldName, defaultStrategy.getClass());
                        }
                    }
                }
                
                if (!fieldMap.isEmpty()) {
                    tableFieldMap.put(tableName, fieldMap);
                    encryptTables.add(tableName);
                }
            }
        }
        
        DATASOURCE_TABLE_FIELD_ENCRYPT_INFO.put(datasourceId, tableFieldMap);
        DATASOURCE_FIELD_ENCRYPT_TABLE.put(datasourceId, encryptTables);
    }
    
    /**
     * 检查表是否需要加密（支持数据源标识）
     */
    public static boolean concatTable(String tableName, String datasourceId) {
        if (StrUtil.isBlank(tableName)) {
            return false;
        }
        
        String pureTableName = extractPureTableName(tableName);
        Set<String> encryptTables = DATASOURCE_FIELD_ENCRYPT_TABLE.getOrDefault(
            datasourceId != null ? datasourceId : "default", 
            Collections.emptySet()
        );
        
        return encryptTables.contains(pureTableName);
    }
    
    /**
     * 获取表字段加密信息（支持数据源标识）
     */
    public static Map<String, Class<? extends FieldEncryptorStrategy>> getTableFieldEncryptInfo(
            String tableName, String datasourceId) {
        if (StrUtil.isBlank(tableName)) {
            return null;
        }
        
        String pureTableName = extractPureTableName(tableName);
        Map<String, Map<String, Class<? extends FieldEncryptorStrategy>>> datasourceConfig = 
            DATASOURCE_TABLE_FIELD_ENCRYPT_INFO.get(datasourceId != null ? datasourceId : "default");
        
        if (datasourceConfig == null) {
            return null;
        }
        
        return datasourceConfig.get(pureTableName);
    }
}
```

#### 2.3.5 连接层改造

**SimpleInterceptorDriver 改造**：
```java
@Override
public Connection connect(String url, Properties info) throws SQLException {
    if (!acceptsURL(url)) {
        return null;
    }
    
    // 提取数据源标识
    String datasourceId = extractDatasourceId(url, info);
    
    // 提取真实 URL
    String realUrl = extractRealUrl(url);
    
    // 查找底层驱动
    Driver underlyingDriver = findUnderlyingDriver(realUrl);
    if (underlyingDriver == null) {
        throw new SQLException("No suitable driver found for " + realUrl);
    }
    
    // 建立真实连接
    Connection realConnection = underlyingDriver.connect(realUrl, info);
    if (realConnection == null) {
        return null;
    }
    
    // 创建包装连接，传递数据源标识
    return new SimpleInterceptorConnection(realConnection, datasourceId);
}

/**
 * 提取数据源标识
 */
private String extractDatasourceId(String url, Properties info) {
    // 1. 优先从 URL 参数中获取
    String datasourceId = extractDatasourceIdFromUrl(url);
    if (StrUtil.isNotBlank(datasourceId)) {
        return datasourceId;
    }
    
    // 2. 从连接属性中获取
    if (info != null) {
        datasourceId = info.getProperty("datasource-id");
        if (StrUtil.isNotBlank(datasourceId)) {
            return datasourceId;
        }
    }
    
    // 3. 返回默认值
    return "default";
}

/**
 * 从 URL 中提取数据源标识
 * 格式：jdbc:interceptor:mysql://...?datasource-id=primary
 */
private String extractDatasourceIdFromUrl(String url) {
    try {
        // 解析 URL 参数
        int questionMarkIndex = url.indexOf('?');
        if (questionMarkIndex < 0) {
            return null;
        }
        
        String queryString = url.substring(questionMarkIndex + 1);
        String[] params = queryString.split("&");
        
        for (String param : params) {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2 && "datasource-id".equals(keyValue[0])) {
                return URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
            }
        }
    } catch (Exception e) {
        log.warn("Failed to extract datasource-id from URL: {}", url, e);
    }
    
    return null;
}
```

**SimpleInterceptorConnection 改造**：
```java
public class SimpleInterceptorConnection implements Connection {
    
    private final Connection delegate;
    
    /**
     * 数据源标识
     */
    private final String datasourceId;
    
    public SimpleInterceptorConnection(Connection delegate, String datasourceId) {
        this.delegate = delegate;
        this.datasourceId = datasourceId != null ? datasourceId : "default";
    }
    
    public String getDatasourceId() {
        return datasourceId;
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        PreparedStatement statement = delegate.prepareStatement(sql);
        // 传递数据源标识
        return new SimpleInterceptorPreparedStatement(statement, sql, datasourceId);
    }
}
```

**SimpleInterceptorPreparedStatement 改造**：
```java
public class SimpleInterceptorPreparedStatement implements PreparedStatement {
    
    private final PreparedStatement delegate;
    private final String sql;
    private final String datasourceId;  // 新增：数据源标识
    
    public SimpleInterceptorPreparedStatement(PreparedStatement delegate, String sql, String datasourceId) {
        this.delegate = delegate;
        this.sql = sql.trim();
        this.datasourceId = datasourceId != null ? datasourceId : "default";
        
        // 解析表名
        Set<String> tables = parseTables(sql);
        
        // 根据数据源标识判断是否需要加密
        if (SecurtkitUtils.needEncrypt(tables, datasourceId)) {
            // 解析 SQL，使用数据源标识
            this.pair = SecurtkitUtils.parseSql(this.sql, datasourceId);
            // ...
        }
    }
    
    // 后续方法都使用 datasourceId 查找配置
}
```

#### 2.3.6 SecurtkitUtils 改造

```java
public class SecurtkitUtils {
    
    /**
     * 判断是否需要加密（支持数据源标识）
     */
    public static boolean needEncrypt(Collection<String> tables, String datasourceId) {
        if (CollectionUtil.isEmpty(tables)) {
            return false;
        }
        
        // 提取纯表名
        Set<String> pureTableNames = tables.stream()
            .map(SecurtkitUtils::extractPureTableName)
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.toSet());
        
        // 根据数据源标识查找配置
        Set<String> configuredTables = TableCache.getTables(datasourceId);
        
        return CollectionUtil.containsAny(configuredTables, pureTableNames);
    }
    
    /**
     * 解析 SQL（支持数据源标识）
     */
    public static Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseSql(
            String sql, String datasourceId) {
        // 解析逻辑保持不变，但在查找表配置时使用 datasourceId
        // ...
    }
}
```

### 2.4 向后兼容性设计

#### 2.4.1 单数据源场景兼容

**旧配置方式（仍然支持）**：
```yaml
securtkit:
  encryptor:
    enable: true
    tables:
      - table-name: user
        fields:
          - field-name: name
```

**统一配置方式**：
```yaml
securtkit:
  encryptor:
    enable: true
    tables:
      - table-name: user
        fields:
          - field-name: name
```

**配置说明**：
- 单数据源场景：不指定 `datasource-id`，配置应用到所有数据源
- 多数据源场景：指定 `datasource-id`，配置只应用到该数据源
- 数据源标识默认为 `"default"`，如果未指定 `datasource-id` 则使用 `"default"`

#### 2.4.2 代码兼容性

- 所有新增方法都提供重载版本，支持不带 `datasourceId` 参数（默认使用 `"default"`）
- 现有代码无需修改即可运行

### 2.5 实现步骤

#### 阶段1：基础改造（1-2周）
1. ✅ 设计数据源标识机制
2. ✅ 改造 `FieldEncryptorProperties` 支持多数据源配置
3. ✅ 创建 `DataSourceConfigManager` 类
4. ✅ 改造 `TableCache` 支持数据源级别缓存

#### 阶段2：连接层改造（1周）
1. ✅ 改造 `SimpleInterceptorDriver` 提取数据源标识
2. ✅ 改造 `SimpleInterceptorConnection` 传递数据源标识
3. ✅ 改造 `SimpleInterceptorPreparedStatement` 使用数据源标识

#### 阶段3：工具类改造（1周）
1. ✅ 改造 `SecurtkitUtils` 支持数据源标识
2. ✅ 改造 `FieldParseParseTableFromItemVisitor` 等解析器

#### 阶段4：测试验证（1-2周）
1. ✅ 单元测试：单数据源场景
2. ✅ 单元测试：多数据源场景
3. ✅ 集成测试：Spring Boot 多数据源集成
4. ✅ 性能测试：配置查找性能

### 2.6 配置示例

#### 示例1：单数据源（向后兼容）
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

#### 示例2：多数据源 - 不同加密策略
```yaml
securtkit:
  encryptor:
    enable: true
    failure-policy: FALLBACK
    tables:
      # 主数据源配置
      - table-name: user
        datasource-id: primary
        fields:
          - field-name: name
            strategy: com.example.AESStrategy
          - field-name: phone
            strategy: com.example.AESStrategy
      
      # 从数据源不配置（等于关闭加密）
```

#### 示例3：多数据源 - 相同表名不同策略
```yaml
securtkit:
  encryptor:
    enable: true
    tables:
      # 主库配置
      - table-name: user
        datasource-id: main_db
        fields:
          - field-name: name
            strategy: com.example.AESStrategy
      
      # 从库配置
      - table-name: user
        datasource-id: read_db
        fields:
          - field-name: name
            strategy: com.example.RSAStrategy
```

#### 示例4：Spring Boot 多数据源配置
```java
@Configuration
public class MultiDataSourceConfig {
    
    @Bean("primaryDataSource")
    @Primary
    public DataSource primaryDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("io.github.hexlodev.core.interceptor.SimpleInterceptorDriver");
        ds.setJdbcUrl("jdbc:interceptor:mysql://localhost:3306/main_db?datasource-id=primary");
        ds.setUsername("root");
        ds.setPassword("password");
        return ds;
    }
    
    @Bean("secondaryDataSource")
    public DataSource secondaryDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("io.github.hexlodev.core.interceptor.SimpleInterceptorDriver");
        ds.setJdbcUrl("jdbc:interceptor:mysql://localhost:3306/read_db?datasource-id=secondary");
        ds.setUsername("readonly");
        ds.setPassword("password");
        return ds;
    }
}
```

### 2.7 性能考虑

1. **配置缓存**：使用 `ConcurrentHashMap` 缓存合并后的配置，避免重复计算
2. **配置查找**：O(1) 时间复杂度
3. **内存占用**：每个数据源配置独立存储，内存占用可控

### 2.8 潜在问题和解决方案

#### 问题1：动态数据源切换
**场景**：使用动态数据源路由（如 MyBatis 的 `@DS` 注解）
**解决方案**：
- 在数据源路由时传递数据源标识
- 或者从线程上下文获取当前数据源标识

#### 问题2：配置热更新
**场景**：运行时修改配置
**解决方案**：
- 提供 `TableCache.reload(datasourceId)` 方法
- 支持配置监听和自动刷新

#### 问题3：配置验证
**场景**：配置错误导致运行时异常
**解决方案**：
- 启动时验证所有数据源配置
- 提供配置验证工具

## 3. 总结

### 3.1 可行性评估

✅ **完全可行**，原因：
1. 核心架构无需大幅改动，只需增加数据源标识传递机制
2. 配置结构向后兼容，现有项目可平滑升级
3. 性能影响可控，配置查找为 O(1)

### 3.2 改进建议

1. **优先级1（必须）**：
   - 数据源标识机制
   - 配置隔离（数据源级别）
   - 向后兼容性

2. **优先级2（推荐）**：
   - 配置继承和覆盖
   - 配置验证工具
   - 单元测试和集成测试

3. **优先级3（可选）**：
   - 配置热更新
   - 动态数据源路由支持
   - 配置管理界面

### 3.3 实施建议

1. **渐进式改造**：先实现基础功能，再逐步完善
2. **充分测试**：确保向后兼容性和功能正确性
3. **文档完善**：提供详细的使用文档和迁移指南

