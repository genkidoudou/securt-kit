package io.github.hexlodev.core;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.StrUtil;
import io.github.hexlodev.core.cache.StrategyCache;
import io.github.hexlodev.core.config.DataSourceConfigManager;
import io.github.hexlodev.core.config.FieldEncryptorProperties;
import io.github.hexlodev.core.exception.EncryptionHandler;
import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class TableCache {
    /**
     * 数据源配置管理器
     */
    private static DataSourceConfigManager configManager;

    /**
     * 表字段加密信息缓存（向后兼容，单数据源场景）
     * key: 表名（小写）
     * value: Map<字段名（小写）, FieldEncryptor注解>
     */
    private static final Map<String, Map<String, Class<? extends FieldEncryptorStrategy>>> TABLE_FIELD_ENCRYPT_INFO = new ConcurrentHashMap<>();

    /**
     * 需要加密的表名集合（向后兼容，单数据源场景）
     * <p>
     * 使用 ConcurrentHashMap.newKeySet() 保证线程安全，性能优于 Collections.synchronizedSet()
     * </p>
     */
    private static final Set<String> FIELD_ENCRYPT_TABLE = ConcurrentHashMap.newKeySet();

    /**
     * 数据源级别的表字段加密信息缓存（多数据源场景）
     * Key: 数据源标识
     * Value: Map<表名（小写）, Map<字段名（小写）, 策略类>>
     */
    private static final Map<String, Map<String, Map<String, Class<? extends FieldEncryptorStrategy>>>> 
        DATASOURCE_TABLE_FIELD_ENCRYPT_INFO = new ConcurrentHashMap<>();

    /**
     * 数据源级别的加密表集合（多数据源场景）
     * Key: 数据源标识
     * Value: Set<表名（小写）>
     */
    private static final Map<String, Set<String>> DATASOURCE_FIELD_ENCRYPT_TABLE = new ConcurrentHashMap<>();


    /**
     * 是否已初始化
     * <p>
     * 使用 AtomicBoolean 保证线程安全的初始化状态管理
     * </p>
     *
     * @author luyanan
     * @since 2025/10/9
     */
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    /**
     * 初始化：先扫描实体（注解），再与配置文件合并
     * 注解优先：同表出现时跳过配置写入
     * <p>
     * 使用原子操作保证线程安全，防止重复初始化
     * </p>
     * 
     * <p>初始化流程：</p>
     * <ol>
     *   <li>验证配置有效性（表名、字段名格式等）</li>
     *   <li>验证加密策略类是否存在且可加载</li>
     *   <li>初始化缓存数据</li>
     *   <li>初始化 SQL 解析缓存</li>
     *   <li>初始化异常处理策略</li>
     * </ol>
     *
     * @param fieldEncryptorProperties 字段加密配置属性，不能为 null
     * @throws IllegalArgumentException 如果配置无效
     * @throws RuntimeException 如果初始化失败
     */
    public static void init(FieldEncryptorProperties fieldEncryptorProperties) {
        // 使用原子操作检查并设置初始化状态，防止并发重复初始化
        if (!INITIALIZED.compareAndSet(false, true)) {
            log.warn("【securt-kit】TableCache already initialized, skipping...");
            return;
        }
        
        try {
            log.debug("【securt-kit】配置文件表缓存开始初始化");
            if (null == fieldEncryptorProperties) {
                log.debug("fieldEncryptorProperties未配置,请先配置");
                // 如果配置为空，重置初始化状态
                INITIALIZED.set(false);
                return;
            }
            
            // 初始化配置管理器（支持多数据源）
            configManager = new DataSourceConfigManager(fieldEncryptorProperties);
            
            // 判断是否使用多数据源配置
            // 新配置方式：tables 中指定了 datasource-id
            // 旧配置方式：使用了 global + datasources 结构
            boolean isMultiDatasource = isMultiDatasourceConfig(fieldEncryptorProperties);

            if (isMultiDatasource) {
                // 多数据源场景：为每个数据源初始化配置
                log.debug("【securt-kit】检测到多数据源配置，开始初始化各数据源配置");
                
                // 初始化所有数据源的配置
                Set<String> datasourceIds = configManager.getDatasourceIds();
                for (String datasourceId : datasourceIds) {
                    initForDatasource(datasourceId);
                }
                
                // 确保默认数据源也被初始化
                if (!datasourceIds.contains(DataSourceConfigManager.DEFAULT_DATASOURCE_ID)) {
                    initForDatasource(DataSourceConfigManager.DEFAULT_DATASOURCE_ID);
                }
            } else {
                // 单数据源场景：向后兼容原有逻辑
                log.debug("【securt-kit】单数据源配置，使用向后兼容模式");
                
                // 验证配置
                validateConfiguration(fieldEncryptorProperties);
                
                // 使用策略缓存获取默认策略实例
                FieldEncryptorStrategy defaultFieldEncryptorStrategy;
                try {
                    defaultFieldEncryptorStrategy = StrategyCache.getStrategy(FieldEncryptorStrategy.class);
                } catch (Exception e) {
                    log.warn("Failed to get default strategy, will use strategy class name instead: {}", e.getMessage());
                    defaultFieldEncryptorStrategy = null;
                }
                
                Map<String, Map<String, Class<? extends FieldEncryptorStrategy>>> parserEntityClass = new HashMap<>();

                // 跟配置文件中的进行合并
                List<FieldEncryptorProperties.TableConfig> tables = fieldEncryptorProperties.getTables();
                if (CollectionUtil.isNotEmpty(tables)) {
                    for (FieldEncryptorProperties.TableConfig table : tables) {
                        Map<String, Class<? extends FieldEncryptorStrategy>> fieldEncryptorMap = new HashMap<>();
                        String tableName = table.getTableName().toLowerCase(Locale.ROOT);
                        List<FieldEncryptorProperties.FieldConfig> fields = table.getFields();
                        if (CollectionUtil.isNotEmpty(fields)) {
                            for (FieldEncryptorProperties.FieldConfig field : fields) {
                                String fieldName = field.getFieldName();
                                String strategy = field.getStrategy();
                                if (StrUtil.isBlank(strategy)) {
                                    if (defaultFieldEncryptorStrategy != null) {
                                        strategy = defaultFieldEncryptorStrategy.getClass().getName();
                                    } else {
                                        log.warn("No default strategy available and no strategy configured for field: {}.{}", tableName, fieldName);
                                        continue;
                                    }
                                }
                                fieldEncryptorMap.put(fieldName, ClassUtil.loadClass(strategy));
                            }
                            if (CollectionUtil.isNotEmpty(fieldEncryptorMap)) {
                                parserEntityClass.put(tableName, fieldEncryptorMap);
                            }
                        }
                    }
                }

                if (CollectionUtil.isNotEmpty(parserEntityClass)) {
                    for (Map.Entry<String, Map<String, Class<? extends FieldEncryptorStrategy>>> stringMapEntry : parserEntityClass.entrySet()) {
                        String tableName = stringMapEntry.getKey();
                        Map<String, Class<? extends FieldEncryptorStrategy>> value = stringMapEntry.getValue();
                        TABLE_FIELD_ENCRYPT_INFO.put(tableName, value);
                        FIELD_ENCRYPT_TABLE.add(tableName);
                    }
                }
            }

            // 初始化 SQL 解析缓存配置（使用全局配置）
            DataSourceConfigManager.MergedConfig globalConfig = configManager.getConfig(DataSourceConfigManager.DEFAULT_DATASOURCE_ID);
            initSqlParseCacheFromMergedConfig(globalConfig);

            // 初始化异常处理策略（使用全局配置）
            EncryptionHandler.initFromConfig(fieldEncryptorProperties);

            log.debug("【securt-kit】配置文件表缓存初始化完成，需处理的表为:{}", TABLE_FIELD_ENCRYPT_INFO);
            log.debug("【securt-kit】多数据源配置缓存: {}", DATASOURCE_TABLE_FIELD_ENCRYPT_INFO.keySet());
        } catch (Exception e) {
            // 初始化失败，重置状态以便下次重试
            log.error("【securt-kit】TableCache initialization failed", e);
            INITIALIZED.set(false);
            // 清空可能已经部分写入的数据
            TABLE_FIELD_ENCRYPT_INFO.clear();
            FIELD_ENCRYPT_TABLE.clear();
            throw new RuntimeException("Failed to initialize TableCache", e);
        }
    }

    /**
     * 判断是否使用多数据源配置
     */
    private static boolean isMultiDatasourceConfig(FieldEncryptorProperties properties) {
        if (properties == null) {
            return false;
        }
        
        // 新配置方式：tables 中指定了 datasource-id
        if (CollectionUtil.isNotEmpty(properties.getTables())) {
            for (FieldEncryptorProperties.TableConfig table : properties.getTables()) {
                if (StrUtil.isNotBlank(table.getDatasourceId())) {
                    return true;
                }
            }
        }
        
        // 旧配置方式：使用了 global + datasources 结构
        if (properties.getGlobal() != null || 
            (properties.getDatasources() != null && !properties.getDatasources().isEmpty())) {
            return true;
        }
        
        return false;
    }

    /**
     * 验证配置的有效性
     * 
     * <p>验证内容包括：</p>
     * <ul>
     *   <li>表名不能为空或空白</li>
     *   <li>表名格式验证（只允许字母、数字、下划线）</li>
     *   <li>字段名不能为空或空白</li>
     *   <li>字段名格式验证（只允许字母、数字、下划线）</li>
     *   <li>策略类名验证（如果配置了策略类名，必须存在且可加载）</li>
     * </ul>
     *
     * @param properties 配置属性
     * @throws IllegalArgumentException 如果配置无效
     */
    private static void validateConfiguration(FieldEncryptorProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("FieldEncryptorProperties cannot be null");
        }

        List<FieldEncryptorProperties.TableConfig> tables = properties.getTables();
        if (CollectionUtil.isEmpty(tables)) {
            log.warn("【securt-kit】警告：未配置任何表，字段加密功能将不会生效");
            return;
        }

        // 表名格式验证：只允许字母、数字、下划线、点号（支持 schema.table 格式）
        java.util.regex.Pattern tableNamePattern = java.util.regex.Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.]*$");
        // 字段名格式验证：只允许字母、数字、下划线
        java.util.regex.Pattern fieldNamePattern = java.util.regex.Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

        List<String> errors = new ArrayList<>();
        int tableIndex = 0;
        
        for (FieldEncryptorProperties.TableConfig table : tables) {
            tableIndex++;
            String tableName = table.getTableName();
            
            // 验证表名
            if (StrUtil.isBlank(tableName)) {
                errors.add(String.format("表配置[%d]: 表名不能为空", tableIndex));
                continue;
            }
            
            // 提取纯表名进行格式验证（去掉 schema 前缀）
            String pureTableName = extractPureTableName(tableName);
            if (!tableNamePattern.matcher(pureTableName).matches()) {
                errors.add(String.format("表配置[%d]: 表名格式无效 '%s'，只允许字母、数字、下划线和点号", 
                        tableIndex, tableName));
            }
            
            // 验证字段配置
            List<FieldEncryptorProperties.FieldConfig> fields = table.getFields();
            if (CollectionUtil.isEmpty(fields)) {
                errors.add(String.format("表配置[%d]: 表 '%s' 未配置任何字段", tableIndex, tableName));
                continue;
            }
            
            int fieldIndex = 0;
            for (FieldEncryptorProperties.FieldConfig field : fields) {
                fieldIndex++;
                String fieldName = field.getFieldName();
                
                // 验证字段名
                if (StrUtil.isBlank(fieldName)) {
                    errors.add(String.format("表配置[%d].字段配置[%d]: 字段名不能为空", tableIndex, fieldIndex));
                    continue;
                }
                
                if (!fieldNamePattern.matcher(fieldName).matches()) {
                    errors.add(String.format("表配置[%d].字段配置[%d]: 字段名格式无效 '%s'，只允许字母、数字和下划线", 
                            tableIndex, fieldIndex, fieldName));
                }
                
                // 验证策略类名（如果配置了）
                String strategy = field.getStrategy();
                if (StrUtil.isNotBlank(strategy)) {
                    try {
                        Class<?> strategyClass = ClassUtil.loadClass(strategy);
                        if (!FieldEncryptorStrategy.class.isAssignableFrom(strategyClass)) {
                            errors.add(String.format("表配置[%d].字段配置[%d]: 策略类 '%s' 未实现 FieldEncryptorStrategy 接口", 
                                    tableIndex, fieldIndex, strategy));
                        }
                    } catch (Exception e) {
                        errors.add(String.format("表配置[%d].字段配置[%d]: 无法加载策略类 '%s'，错误: %s", 
                                tableIndex, fieldIndex, strategy, e.getMessage()));
                    }
                }
            }
        }
        
        // 如果有错误，抛出异常
        if (!errors.isEmpty()) {
            StringBuilder errorMsg = new StringBuilder("配置验证失败，发现以下错误：\n");
            for (int i = 0; i < errors.size(); i++) {
                errorMsg.append(String.format("  [%d] %s\n", i + 1, errors.get(i)));
            }
            errorMsg.append("\n请检查配置文件并修复上述错误。");
            throw new IllegalArgumentException(errorMsg.toString());
        }
        
        log.debug("【securt-kit】配置验证通过，共配置 {} 个表", tables.size());
    }

    /**
     * 初始化 SQL 解析缓存配置
     *
     * @param properties 配置属性
     */
    private static void initSqlParseCache(FieldEncryptorProperties properties) {
        if (properties == null) {
            return;
        }

        FieldEncryptorProperties.SqlParseCacheConfig cacheConfig = properties.getSqlParseCache();
        if (cacheConfig == null) {
            // 如果未配置，使用默认值（启用缓存，容量1000）
            io.github.hexlodev.core.parser.SqlParseCache.init(true, 1000);
            log.debug("【securt-kit】SQL 解析缓存使用默认配置: enable=true, maxSize=1000");
        } else {
            io.github.hexlodev.core.parser.SqlParseCache.init(
                    cacheConfig.isEnable(),
                    cacheConfig.getMaxSize()
            );
            log.debug("【securt-kit】SQL 解析缓存配置: enable={}, maxSize={}",
                    cacheConfig.isEnable(), cacheConfig.getMaxSize());
        }
    }

    /**
     * 从合并配置初始化 SQL 解析缓存配置
     *
     * @param mergedConfig 合并后的配置
     */
    private static void initSqlParseCacheFromMergedConfig(DataSourceConfigManager.MergedConfig mergedConfig) {
        if (mergedConfig == null) {
            io.github.hexlodev.core.parser.SqlParseCache.init(true, 1000);
            return;
        }

        FieldEncryptorProperties.SqlParseCacheConfig cacheConfig = mergedConfig.getSqlParseCache();
        if (cacheConfig == null) {
            io.github.hexlodev.core.parser.SqlParseCache.init(true, 1000);
            log.debug("【securt-kit】SQL 解析缓存使用默认配置: enable=true, maxSize=1000");
        } else {
            io.github.hexlodev.core.parser.SqlParseCache.init(
                    cacheConfig.isEnable(),
                    cacheConfig.getMaxSize()
            );
            log.debug("【securt-kit】SQL 解析缓存配置: enable={}, maxSize={}",
                    cacheConfig.isEnable(), cacheConfig.getMaxSize());
        }
    }

    /**
     * 为指定数据源初始化配置（多数据源场景）
     *
     * @param datasourceId 数据源标识
     */
    private static void initForDatasource(String datasourceId) {
        if (configManager == null) {
            log.warn("ConfigManager not initialized, cannot init datasource config for: {}", datasourceId);
            return;
        }

        DataSourceConfigManager.MergedConfig config = configManager.getConfig(datasourceId);
        if (config == null || (config.getEnable() != null && !config.getEnable())) {
            log.debug("Datasource '{}' encryption is disabled or config not found", datasourceId);
            return;
        }

        // 获取默认策略
        FieldEncryptorStrategy defaultFieldEncryptorStrategy;
        try {
            defaultFieldEncryptorStrategy = StrategyCache.getStrategy(FieldEncryptorStrategy.class);
        } catch (Exception e) {
            log.warn("Failed to get default strategy: {}", e.getMessage());
            defaultFieldEncryptorStrategy = null;
        }

        // 解析表配置
        Map<String, Map<String, Class<? extends FieldEncryptorStrategy>>> tableFieldMap = new HashMap<>();
        Set<String> encryptTables = new HashSet<>();

        if (CollectionUtil.isNotEmpty(config.getTables())) {
            for (FieldEncryptorProperties.TableConfig table : config.getTables()) {
                // 提取纯表名（去掉双引号、数据库名、schema前缀）
                String tableName = extractPureTableName(table.getTableName());
                Map<String, Class<? extends FieldEncryptorStrategy>> fieldMap = new HashMap<>();

                if (CollectionUtil.isNotEmpty(table.getFields())) {
                    for (FieldEncryptorProperties.FieldConfig field : table.getFields()) {
                        String fieldName = field.getFieldName().toLowerCase(Locale.ROOT);
                        String strategy = field.getStrategy();
                        if (StrUtil.isBlank(strategy)) {
                            if (defaultFieldEncryptorStrategy != null) {
                                strategy = defaultFieldEncryptorStrategy.getClass().getName();
                            } else {
                                log.warn("No default strategy available for field: {}.{}", tableName, fieldName);
                                continue;
                            }
                        }
                        try {
                            fieldMap.put(fieldName, ClassUtil.loadClass(strategy));
                        } catch (Exception e) {
                            log.error("Failed to load strategy class '{}' for field {}.{}: {}", 
                                    strategy, tableName, fieldName, e.getMessage());
                        }
                    }
                }

                if (!fieldMap.isEmpty()) {
                    tableFieldMap.put(tableName, fieldMap);
                    encryptTables.add(tableName);
                }
            }
        }

        if (!tableFieldMap.isEmpty()) {
            DATASOURCE_TABLE_FIELD_ENCRYPT_INFO.put(datasourceId, tableFieldMap);
            DATASOURCE_FIELD_ENCRYPT_TABLE.put(datasourceId, encryptTables);
            log.debug("Initialized datasource '{}' config: {} tables, {} fields", 
                    datasourceId, encryptTables.size(), 
                    tableFieldMap.values().stream().mapToInt(Map::size).sum());
        }
    }


    /**
     * 获取加密的表名集合
     * <p>
     * 返回不可变视图，防止外部代码意外修改内部状态。
     * 底层集合使用 ConcurrentHashMap.newKeySet() 实现，保证线程安全。
     * </p>
     *
     * @return 加密表名集合的不可变视图
     * @since 2025/10/9
     */
    public static Set<String> getTables() {
        // 返回不可变视图，防止外部修改，同时保持线程安全
        return Collections.unmodifiableSet(FIELD_ENCRYPT_TABLE);
    }


    /**
     * 从表名中提取纯表名（去掉数据库名和schema前缀，以及双引号）
     * 
     * <p>支持的表名格式：</p>
     * <ul>
     *   <li>{@code database.table} -> {@code table}</li>
     *   <li>{@code schema.table} -> {@code table}</li>
     *   <li>{@code database.schema.table} -> {@code table}</li>
     *   <li>{@code "table"} -> {@code table}（去掉双引号）</li>
     *   <li>{@code table} -> {@code table}（已经是纯表名）</li>
     * </ul>
     *
     * @param tableName 表名，可能包含数据库名、schema前缀或双引号
     * @return 纯表名（小写，已去掉双引号），如果输入为空则返回原值
     */
    private static String extractPureTableName(String tableName) {
        if (StrUtil.isBlank(tableName)) {
            return tableName;
        }
        
        // 转换为小写并去除首尾空白
        String lowerTableName = tableName.toLowerCase().trim();
        
        // 去掉双引号（如果存在）
        if (lowerTableName.startsWith("\"") && lowerTableName.endsWith("\"")) {
            lowerTableName = lowerTableName.substring(1, lowerTableName.length() - 1);
        }
        
        // 如果包含点号，取最后一个点号后的部分作为表名
        int lastDotIndex = lowerTableName.lastIndexOf('.');
        if (lastDotIndex >= 0 && lastDotIndex < lowerTableName.length() - 1) {
            return lowerTableName.substring(lastDotIndex + 1);
        }
        
        // 如果没有点号，说明已经是纯表名
        return lowerTableName;
    }

    /**
     * 检查表是否需要加密（向后兼容，使用默认数据源）
     * 
     * <p>支持带数据库名的表名格式（如 testdb.user），会自动提取纯表名进行匹配。</p>
     *
     * @param tableName 表名，不能为 null 或空白，可能包含数据库名和schema前缀
     * @return 如果表需要加密返回 true，否则返回 false
     * @throws IllegalArgumentException 如果表名为 null 或空白
     * @since 2025/10/10
     */
    public static boolean concatTable(String tableName) {
        return concatTable(tableName, null);
    }

    /**
     * 检查表是否需要加密（支持数据源标识）
     * 
     * <p>支持带数据库名的表名格式（如 testdb.user），会自动提取纯表名进行匹配。</p>
     *
     * @param tableName 表名，不能为 null 或空白，可能包含数据库名和schema前缀
     * @param datasourceId 数据源标识，如果为 null 则使用默认数据源
     * @return 如果表需要加密返回 true，否则返回 false
     * @throws IllegalArgumentException 如果表名为 null 或空白
     * @since 1.1.0
     */
    public static boolean concatTable(String tableName, String datasourceId) {
        if (StrUtil.isBlank(tableName)) {
            throw new IllegalArgumentException("Table name cannot be null or blank");
        }
        
        String pureTableName = extractPureTableName(tableName);
        String dsId = StrUtil.isBlank(datasourceId) ? DataSourceConfigManager.DEFAULT_DATASOURCE_ID : datasourceId;
        
        // 优先使用多数据源缓存
        Set<String> encryptTables = DATASOURCE_FIELD_ENCRYPT_TABLE.get(dsId);
        if (encryptTables != null) {
            return encryptTables.contains(pureTableName);
        }
        
        // 向后兼容：使用单数据源缓存
        return getTables().contains(pureTableName);
    }


    /**
     * 检查是否已初始化
     *
     * @return true 如果已初始化，false 否则
     */
    public static boolean isInit() {
        return INITIALIZED.get();
    }

    /**
     * 重置初始化状态（用于测试或配置热更新）
     * <p>
     * 清空所有缓存数据并将初始化状态重置为 false
     * </p>
     */
    public static void reset() {
        INITIALIZED.set(false);
        TABLE_FIELD_ENCRYPT_INFO.clear();
        FIELD_ENCRYPT_TABLE.clear();
        DATASOURCE_TABLE_FIELD_ENCRYPT_INFO.clear();
        DATASOURCE_FIELD_ENCRYPT_TABLE.clear();
        configManager = null;
        log.info("【securt-kit】TableCache reset completed");
    }

    /**
     * 获取表加密的字段（向后兼容，使用默认数据源）
     * 
     * <p>支持带数据库名的表名格式（如 testdb.user），会自动提取纯表名进行查找。</p>
     *
     * @param tableName 表名，不能为 null 或空白，可能包含数据库名和schema前缀
     * @return 字段加密策略映射，如果表不存在或参数无效则返回 null
     * @throws IllegalArgumentException 如果表名为 null 或空白
     * @since 2025/10/10
     */
    public static Map<String, Class<? extends FieldEncryptorStrategy>> getTableFieldEncryptInfo(String tableName) {
        return getTableFieldEncryptInfo(tableName, null);
    }

    /**
     * 获取表加密的字段（支持数据源标识）
     * 
     * <p>支持带数据库名的表名格式（如 testdb.user），会自动提取纯表名进行查找。</p>
     *
     * @param tableName 表名，不能为 null 或空白，可能包含数据库名和schema前缀
     * @param datasourceId 数据源标识，如果为 null 则使用默认数据源
     * @return 字段加密策略映射，如果表不存在或参数无效则返回 null
     * @throws IllegalArgumentException 如果表名为 null 或空白
     * @since 1.1.0
     */
    public static Map<String, Class<? extends FieldEncryptorStrategy>> getTableFieldEncryptInfo(
            String tableName, String datasourceId) {
        if (StrUtil.isBlank(tableName)) {
            throw new IllegalArgumentException("Table name cannot be null or blank");
        }
        
        String pureTableName = extractPureTableName(tableName);
        String dsId = StrUtil.isBlank(datasourceId) ? DataSourceConfigManager.DEFAULT_DATASOURCE_ID : datasourceId;
        
        // 优先使用多数据源缓存
        Map<String, Map<String, Class<? extends FieldEncryptorStrategy>>> datasourceConfig = 
            DATASOURCE_TABLE_FIELD_ENCRYPT_INFO.get(dsId);
        if (datasourceConfig != null) {
            return datasourceConfig.get(pureTableName);
        }
        
        // 向后兼容：使用单数据源缓存
        return TABLE_FIELD_ENCRYPT_INFO.get(pureTableName);
    }


    /**
     * 根据表名获取加密的字段名列表
     * 
     * <p>支持带数据库名的表名格式（如 testdb.user），会自动提取纯表名进行查找。</p>
     *
     * @param tableName 表名，不能为 null 或空白，可能包含数据库名和schema前缀
     * @return 加密字段名列表，如果表不存在或参数无效则返回 null
     * @throws IllegalArgumentException 如果表名为 null 或空白
     * @since 2025/10/12
     */
    public static List<String> getTableFieldName(String tableName) {
        if (StrUtil.isBlank(tableName)) {
            throw new IllegalArgumentException("Table name cannot be null or blank");
        }
        // 提取纯表名进行查找
        String pureTableName = extractPureTableName(tableName);
        Map<String, Class<? extends FieldEncryptorStrategy>> stringClassMap =
                TABLE_FIELD_ENCRYPT_INFO.get(pureTableName);
        if (null == stringClassMap) {
            return null;
        }
        return new ArrayList<>(stringClassMap.keySet());
    }


    /**
     * 获取表字段的加密策略（向后兼容，使用默认数据源）
     * 
     * <p>支持带数据库名的表名格式（如 testdb.user），会自动提取纯表名进行查找。</p>
     *
     * @param tableName 表名，不能为 null 或空白，可能包含数据库名和schema前缀
     * @param fieldName 字段名，不能为 null 或空白
     * @return 加密策略类，如果表或字段不存在或参数无效则返回 null
     * @throws IllegalArgumentException 如果表名或字段名为 null 或空白
     * @since 2025/10/10
     */
    public static Class<? extends FieldEncryptorStrategy> getTableFieldEncryptStrategy(String tableName, String fieldName) {
        return getTableFieldEncryptStrategy(tableName, fieldName, null);
    }

    /**
     * 获取表字段的加密策略（支持数据源标识）
     * 
     * <p>支持带数据库名的表名格式（如 testdb.user），会自动提取纯表名进行查找。</p>
     *
     * @param tableName 表名，不能为 null 或空白，可能包含数据库名和schema前缀
     * @param fieldName 字段名，不能为 null 或空白
     * @param datasourceId 数据源标识，如果为 null 则使用默认数据源
     * @return 加密策略类，如果表或字段不存在或参数无效则返回 null
     * @throws IllegalArgumentException 如果表名或字段名为 null 或空白
     * @since 1.1.0
     */
    public static Class<? extends FieldEncryptorStrategy> getTableFieldEncryptStrategy(
            String tableName, String fieldName, String datasourceId) {
        if (StrUtil.isBlank(tableName)) {
            throw new IllegalArgumentException("Table name cannot be null or blank");
        }
        if (StrUtil.isBlank(fieldName)) {
            throw new IllegalArgumentException("Field name cannot be null or blank");
        }
        
        String pureTableName = extractPureTableName(tableName);
        String dsId = StrUtil.isBlank(datasourceId) ? DataSourceConfigManager.DEFAULT_DATASOURCE_ID : datasourceId;
        
        // 优先使用多数据源缓存
        Map<String, Map<String, Class<? extends FieldEncryptorStrategy>>> datasourceConfig = 
            DATASOURCE_TABLE_FIELD_ENCRYPT_INFO.get(dsId);
        if (datasourceConfig != null) {
            Map<String, Class<? extends FieldEncryptorStrategy>> tableConfig = datasourceConfig.get(pureTableName);
            if (tableConfig != null) {
                return tableConfig.get(fieldName.toLowerCase());
            }
        }
        
        // 向后兼容：使用单数据源缓存
        Map<String, Class<? extends FieldEncryptorStrategy>> stringClassMap = 
            TABLE_FIELD_ENCRYPT_INFO.get(pureTableName);
        if (null == stringClassMap) {
            return null;
        }
        return stringClassMap.get(fieldName.toLowerCase());
    }

}
