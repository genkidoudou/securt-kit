# 项目优化分析报告

## 📋 概述

本报告对 `securt-kit-core`、`securt-kit-starter`、`securt-kit-ui` 三个模块进行了全面分析，识别出可以优化的地方，并提供了具体的优化建议。

**分析日期**: 2025-11-07  
**分析范围**: Core、Starter、UI 三个模块

---

## 🔍 Core 模块优化建议

### 1. 静态方法过多，可改为实例方法

**问题描述**：
- `TableCache`、`ConfigInitializer`、`SqlParseCache`、`SecurtkitUtils` 等类大量使用静态方法
- 静态方法不利于测试、依赖注入和扩展

**影响**：
- 难以进行单元测试（无法 mock）
- 无法使用依赖注入
- 不利于多实例场景

**优化建议**：
```java
// 当前：静态方法
public static void init(FieldEncryptorProperties properties) { ... }

// 优化：实例方法 + 单例模式
public class ConfigInitializer {
    private static final ConfigInitializer INSTANCE = new ConfigInitializer();
    
    public static ConfigInitializer getInstance() {
        return INSTANCE;
    }
    
    public void initialize(FieldEncryptorProperties properties) { ... }
}
```

**优先级**: ⭐⭐⭐ (高)

---

### 2. SecurtkitUtils 类过大，需要拆分

**问题描述**：
- `SecurtkitUtils` 类包含多个职责：
  - SQL 解析入口
  - 占位符替换
  - SQL 规范化
  - 缓存管理（通过 SqlParseCache）

**当前代码行数**: 约 468 行

**优化建议**：
```java
// 拆分为多个类：
// 1. SqlParser - SQL 解析核心逻辑
public class SqlParser {
    public Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parse(String sql) { ... }
}

// 2. PlaceholderReplacer - 占位符替换
public class PlaceholderReplacer {
    public String replacePlaceholders(String sql) { ... }
}

// 3. SqlNormalizer - SQL 规范化
public class SqlNormalizer {
    public String normalize(String sql) { ... }
}

// 4. SecurtkitUtils - 门面类（保持向后兼容）
public class SecurtkitUtils {
    private static final SqlParser parser = new SqlParser();
    private static final PlaceholderReplacer replacer = new PlaceholderReplacer();
    private static final SqlNormalizer normalizer = new SqlNormalizer();
    
    public static Pair<...> parseSql(String sql, String datasourceId) {
        // 委托给各个组件
    }
}
```

**优先级**: ⭐⭐⭐ (高)

---

### 3. 工具类与 Hutool 重复

**问题描述**：
- `StringUtils`、`CollectionUtils` 与 Hutool 提供的工具类功能重复
- 项目已依赖 Hutool，但未充分利用

**优化建议**：
```java
// 当前：自定义工具类
public class StringUtils {
    public static boolean isBlank(String str) { ... }
}

// 优化：直接使用 Hutool
import cn.hutool.core.util.StrUtil;

// 替换所有 StringUtils.isBlank() 为 StrUtil.isBlank()
// 替换所有 CollectionUtils.isEmpty() 为 CollectionUtil.isEmpty()
```

**优先级**: ⭐⭐ (中)

---

### 4. 异常处理可以更细化

**问题描述**：
- 异常类型较多，但处理逻辑可以更统一
- 某些异常缺少上下文信息

**优化建议**：
```java
// 1. 统一异常基类
public abstract class SecurtKitException extends RuntimeException {
    private final String errorCode;
    private final Map<String, Object> context;
    
    public SecurtKitException(String errorCode, String message, Map<String, Object> context) {
        super(message);
        this.errorCode = errorCode;
        this.context = context != null ? context : new HashMap<>();
    }
}

// 2. 具体异常类
public class EncryptionException extends SecurtKitException {
    public EncryptionException(String message, String tableName, String fieldName) {
        super("ENCRYPTION_FAILED", message, 
              Map.of("tableName", tableName, "fieldName", fieldName));
    }
}

// 3. 异常处理器增强
public class EncryptionHandler {
    public static <T> T handleException(Supplier<T> operation, 
                                        String tableName, 
                                        String fieldName,
                                        FailurePolicy policy) {
        try {
            return operation.get();
        } catch (Exception e) {
            EncryptionException ex = new EncryptionException(
                e.getMessage(), tableName, fieldName);
            return handleFailure(ex, policy);
        }
    }
}
```

**优先级**: ⭐⭐ (中)

---

### 5. 日志记录可以进一步优化

**问题描述**：
- 已统一使用 `SqlLogger`，但可以添加更多上下文信息
- 某些关键操作缺少日志

**优化建议**：
```java
// 1. 添加结构化日志
public class SqlLogger {
    public static void logEncryption(String tableName, String fieldName, 
                                     String datasourceId, 
                                     int originalLength, int encryptedLength,
                                     long duration) {
        if (log.isDebugEnabled()) {
            log.debug("{} [ENCRYPTION] table={}, field={}, datasource={}, " +
                     "originalLength={}, encryptedLength={}, duration={}ms",
                     LOG_PREFIX, tableName, fieldName, datasourceId,
                     originalLength, encryptedLength, duration);
        }
    }
}

// 2. 添加性能监控日志
public class PerformanceLogger {
    public static void logSlowQuery(String sql, long duration, int threshold) {
        if (duration > threshold) {
            log.warn("{} [SLOW_QUERY] sql={}, duration={}ms, threshold={}ms",
                    LOG_PREFIX, sql, duration, threshold);
        }
    }
}
```

**优先级**: ⭐ (低)

---

## 🔍 Starter 模块优化建议

### 1. 自动配置可以更完善

**问题描述**：
- `SecurtKitAutoConfiguration` 类非常简单，只有配置属性启用
- 缺少条件配置和 Bean 创建逻辑

**优化建议**：
```java
@Configuration
@ConditionalOnProperty(prefix = "securtkit.encryptor", name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FieldEncryptorProperties.class)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
public class SecurtKitAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public ConfigInitializer configInitializer(FieldEncryptorProperties properties) {
        return ConfigInitializer.getInstance();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public DataSourceConfigManager dataSourceConfigManager(FieldEncryptorProperties properties) {
        return new DataSourceConfigManager(properties);
    }
    
    @Bean
    @ConditionalOnProperty(prefix = "securtkit.encryptor.sql-parse-cache", name = "enable", havingValue = "true")
    public SqlParseCache sqlParseCache(FieldEncryptorProperties properties) {
        // 初始化 SQL 解析缓存
        return SqlParseCache.getInstance();
    }
}
```

**优先级**: ⭐⭐ (中)

---

### 2. 添加配置验证

**问题描述**：
- 配置属性缺少验证逻辑
- 配置错误时缺少明确的错误提示

**优化建议**：
```java
@ConfigurationProperties(prefix = "securtkit.encryptor")
@Validated
public class FieldEncryptorProperties {
    
    @NotNull(message = "enable 属性不能为 null")
    private Boolean enable = true;
    
    @Valid
    @NotEmpty(message = "tables 配置不能为空")
    private List<TableConfig> tables;
    
    // 自定义验证方法
    @PostConstruct
    public void validate() {
        if (enable && (tables == null || tables.isEmpty())) {
            throw new ConfigurationException(
                "启用加密时，tables 配置不能为空");
        }
    }
}
```

**优先级**: ⭐⭐ (中)

---

## 🔍 UI 模块优化建议

### 1. MonitorController 类过大，需要拆分

**问题描述**：
- `MonitorController` 类包含 1300+ 行代码
- 包含多个职责：
  - 登录管理
  - 加密/解密接口
  - SQL 解析接口
  - SQL 加密接口
  - SQL 查询接口
  - 配置信息接口

**优化建议**：
```java
// 1. 拆分 Controller
@RestController
@RequestMapping("${securtkit.monitor.path:/monitor}/api/crypto")
public class CryptoController {
    // 加密/解密相关接口
}

@RestController
@RequestMapping("${securtkit.monitor.path:/monitor}/api/sql")
public class SqlController {
    // SQL 解析、加密、查询相关接口
}

@RestController
@RequestMapping("${securtkit.monitor.path:/monitor}/api/config")
public class ConfigController {
    // 配置信息相关接口
}

// 2. 提取 Service 层
@Service
public class CryptoService {
    public EncryptResponse encrypt(EncryptRequest request) { ... }
    public DecryptResponse decrypt(DecryptRequest request) { ... }
}

@Service
public class SqlService {
    public ParseSqlResponse parseSql(ParseSqlRequest request) { ... }
    public EncryptSqlResponse encryptSql(EncryptSqlRequest request) { ... }
    public QuerySqlResponse querySql(QuerySqlRequest request) { ... }
}

// 3. 提取公共逻辑
@Component
public class MonitorAuthService {
    public boolean checkLogin(HttpSession session) { ... }
    public void setLogin(HttpSession session) { ... }
    public void clearLogin(HttpSession session) { ... }
}
```

**优先级**: ⭐⭐⭐ (高)

---

### 2. 代码重复：登录检查

**问题描述**：
- 每个接口方法都重复登录检查逻辑
- 可以使用 AOP 或拦截器统一处理

**优化建议**：
```java
// 1. 使用 AOP
@Aspect
@Component
public class MonitorAuthAspect {
    
    @Autowired
    private MonitorAuthService authService;
    
    @Around("@annotation(RequireLogin)")
    public Object checkLogin(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpSession session = getSession(joinPoint);
        if (!authService.checkLogin(session)) {
            return ApiResponse.error(401, "未登录");
        }
        return joinPoint.proceed();
    }
}

// 2. 使用注解
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireLogin {
}

// 3. 使用方式
@PostMapping("/api/encrypt.json")
@RequireLogin
public ApiResponse<EncryptResponse> encrypt(@Valid @RequestBody EncryptRequest request) {
    // 不需要手动检查登录
}
```

**优先级**: ⭐⭐⭐ (高)

---

### 3. 异常处理可以更统一

**问题描述**：
- 每个接口方法都有 try-catch，代码重复
- 异常处理逻辑不统一

**优化建议**：
```java
// 1. 使用全局异常处理器
@RestControllerAdvice
public class MonitorExceptionHandler {
    
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数错误: {}", e.getMessage());
        return ApiResponse.error(400, "参数错误: " + e.getMessage());
    }
    
    @ExceptionHandler(SQLException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleSqlException(SQLException e) {
        log.error("SQL 执行失败", e);
        return ApiResponse.error(500, "SQL 执行失败: " + e.getMessage());
    }
    
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleException(Exception e) {
        log.error("未知错误", e);
        return ApiResponse.error(500, "系统错误: " + e.getMessage());
    }
}

// 2. Controller 方法简化
@PostMapping("/api/encrypt.json")
@RequireLogin
public ApiResponse<EncryptResponse> encrypt(@Valid @RequestBody EncryptRequest request) {
    // 不需要 try-catch，由全局异常处理器处理
    EncryptResponse response = cryptoService.encrypt(request);
    return ApiResponse.success(response);
}
```

**优先级**: ⭐⭐⭐ (高)

---

### 4. SQL 查询逻辑可以提取

**问题描述**：
- SQL 查询逻辑在 Controller 中，应该提取到 Service 层
- 查询逻辑可以复用

**优化建议**：
```java
@Service
public class SqlQueryService {
    
    @Autowired(required = false)
    private DataSource dataSource;
    
    public QuerySqlResponse querySql(String sql, String datasourceId, 
                                     int pageSize, int pageNum) {
        // 1. 验证 SQL
        SqlValidator.ValidationResult validation = SqlValidator.validateSelectSql(sql);
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getErrorMessage());
        }
        
        // 2. 执行查询
        try (Connection conn = getConnection(datasourceId);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            // 3. 处理结果
            return buildResponse(rs, pageSize, pageNum);
        }
    }
    
    private Connection getConnection(String datasourceId) {
        // 根据 datasourceId 获取对应的连接
    }
    
    private QuerySqlResponse buildResponse(ResultSet rs, int pageSize, int pageNum) {
        // 构建响应对象
    }
}
```

**优先级**: ⭐⭐ (中)

---

### 5. 前端代码可以优化

**问题描述**：
- `app.js` 文件较大（1100+ 行）
- 可以拆分为多个模块

**优化建议**：
```javascript
// 1. 拆分为多个模块
// datasource-manager.js
export class DatasourceManager {
    // 数据源管理逻辑
}

// crypto-service.js
export class CryptoService {
    // 加密解密逻辑
}

// sql-service.js
export class SqlService {
    // SQL 相关逻辑
}

// config-service.js
export class ConfigService {
    // 配置信息逻辑
}

// app.js - 主入口
import { DatasourceManager } from './datasource-manager.js';
import { CryptoService } from './crypto-service.js';
// ...
```

**优先级**: ⭐ (低)

---

## 📊 优化优先级总结

### 高优先级 (⭐⭐⭐)

1. **Core**: 静态方法改为实例方法
2. **Core**: 拆分 `SecurtkitUtils` 类
3. **UI**: 拆分 `MonitorController` 类
4. **UI**: 使用 AOP 统一登录检查
5. **UI**: 统一异常处理

### 中优先级 (⭐⭐)

1. **Core**: 使用 Hutool 替代自定义工具类
2. **Core**: 细化异常处理
3. **Starter**: 完善自动配置
4. **Starter**: 添加配置验证
5. **UI**: 提取 SQL 查询逻辑到 Service 层

### 低优先级 (⭐)

1. **Core**: 进一步优化日志记录
2. **UI**: 前端代码模块化

---

## 🎯 实施建议

### 第一阶段（立即实施）
1. UI 模块：拆分 `MonitorController`，提取 Service 层
2. UI 模块：使用 AOP 统一登录检查
3. UI 模块：统一异常处理

### 第二阶段（近期实施）
1. Core 模块：拆分 `SecurtkitUtils` 类
2. Core 模块：使用 Hutool 替代自定义工具类
3. Starter 模块：完善自动配置

### 第三阶段（长期规划）
1. Core 模块：静态方法改为实例方法
2. Core 模块：细化异常处理
3. UI 模块：前端代码模块化

---

## 📝 注意事项

1. **向后兼容性**: 所有优化都要保持向后兼容，特别是公共 API
2. **测试覆盖**: 优化后需要补充单元测试和集成测试
3. **文档更新**: 优化后需要更新相关文档
4. **性能影响**: 优化时要考虑性能影响，避免引入性能问题

---

**报告生成时间**: 2025-11-07  
**下次审查时间**: 建议 1 个月后再次审查
