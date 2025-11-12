package io.github.hexlodev.core.config;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.StrUtil;
import io.github.hexlodev.core.cache.StrategyCache;
import io.github.hexlodev.core.exception.EncryptionHandler;
import io.github.hexlodev.core.parser.SqlParseCache;
import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
import io.github.hexlodev.core.config.TableConfigRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 配置初始化器
 * <p>
 * 负责配置验证、配置初始化、SQL 解析缓存初始化、异常处理初始化
 * </p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Slf4j
public class ConfigInitializer {

    /**
     * 是否已初始化
     */
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    /**
     * 数据源配置管理器
     */
    private static DataSourceConfigManager configManager;

    /**
     * 是否忽略表名大小写
     */
    private static volatile boolean IGNORE_TABLE_CASE = true;

    /**
     * 初始化配置
     *
     * @param properties 字段加密配置属性
     * @throws IllegalArgumentException 如果配置无效
     * @throws RuntimeException 如果初始化失败
     */
    public static void initialize(FieldEncryptorProperties properties) {
        // 使用原子操作检查并设置初始化状态，防止并发重复初始化
        if (!INITIALIZED.compareAndSet(false, true)) {
            log.warn("【securt-kit】ConfigInitializer already initialized, skipping...");
            return;
        }

        try {
            log.debug("【securt-kit】配置初始化开始");
            if (null == properties) {
                log.debug("fieldEncryptorProperties未配置,请先配置");
                // 如果配置为空，重置初始化状态
                INITIALIZED.set(false);
                return;
            }

            // 初始化配置管理器
            configManager = new DataSourceConfigManager(properties);
            IGNORE_TABLE_CASE = properties.isIgnoreTableCase();

            // 验证配置
            validateConfiguration(properties);

            // 判断是否使用多数据源配置（tables 中指定了 datasource-id）
            boolean isMultiDatasource = isMultiDatasourceConfig(properties);

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
                // 单数据源场景：初始化默认数据源配置
                log.debug("【securt-kit】单数据源配置");
                initForDatasource(DataSourceConfigManager.DEFAULT_DATASOURCE_ID);
            }

            // 初始化 SQL 解析缓存配置（使用全局配置）
            DataSourceConfigManager.MergedConfig globalConfig = configManager.getConfig(DataSourceConfigManager.DEFAULT_DATASOURCE_ID);
            initSqlParseCache(globalConfig);
            SqlParseCache.configureCaseSensitivity(IGNORE_TABLE_CASE);

            // 初始化异常处理策略（使用全局配置）
            EncryptionHandler.initFromConfig(properties);

            log.debug("【securt-kit】配置初始化完成");
        } catch (Exception e) {
            // 初始化失败，重置状态以便下次重试
            log.error("【securt-kit】ConfigInitializer initialization failed", e);
            INITIALIZED.set(false);
            throw new RuntimeException("Failed to initialize ConfigInitializer", e);
        }
    }

    /**
     * 验证配置的有效性
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
     * 判断是否使用多数据源配置
     *
     * @param properties 配置属性
     * @return 如果 tables 中指定了 datasource-id，返回 true
     */
    private static boolean isMultiDatasourceConfig(FieldEncryptorProperties properties) {
        if (properties == null) {
            return false;
        }

        // 检查 tables 中是否指定了 datasource-id
        if (CollectionUtil.isNotEmpty(properties.getTables())) {
            for (FieldEncryptorProperties.TableConfig table : properties.getTables()) {
                if (StrUtil.isNotBlank(table.getDatasourceId())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 为指定数据源初始化配置
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
            // 注册到 TableConfigRegistry
            for (Map.Entry<String, Map<String, Class<? extends FieldEncryptorStrategy>>> entry : tableFieldMap.entrySet()) {
                TableConfigRegistry.registerTable(datasourceId, entry.getKey(), entry.getValue());
            }

            log.debug("Initialized datasource '{}' config: {} tables, {} fields",
                    datasourceId, encryptTables.size(),
                    tableFieldMap.values().stream().mapToInt(Map::size).sum());
        }
    }

    /**
     * 初始化 SQL 解析缓存
     *
     * @param mergedConfig 合并后的配置
     */
    private static void initSqlParseCache(DataSourceConfigManager.MergedConfig mergedConfig) {
        if (mergedConfig == null) {
            SqlParseCache.init(true, 1000);
            SqlParseCache.configureCaseSensitivity(IGNORE_TABLE_CASE);
            return;
        }

        FieldEncryptorProperties.SqlParseCacheConfig cacheConfig = mergedConfig.getSqlParseCache();
        if (cacheConfig == null) {
            SqlParseCache.init(true, 1000);
            SqlParseCache.configureCaseSensitivity(IGNORE_TABLE_CASE);
            log.debug("【securt-kit】SQL 解析缓存使用默认配置: enable=true, maxSize=1000");
        } else {
            SqlParseCache.init(
                    cacheConfig.isEnable(),
                    cacheConfig.getMaxSize()
            );
            SqlParseCache.configureCaseSensitivity(IGNORE_TABLE_CASE);
            log.debug("【securt-kit】SQL 解析缓存配置: enable={}, maxSize={}",
                    cacheConfig.isEnable(), cacheConfig.getMaxSize());
        }
    }

    /**
     * 提取纯表名（去掉数据库名和schema前缀，以及双引号）
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
    public static String extractPureTableName(String tableName) {
        if (StrUtil.isBlank(tableName)) {
            return tableName;
        }

        // 去除首尾空白
        String normalizedTableName = tableName.trim();

        // 去掉双引号（如果存在）
        if (normalizedTableName.startsWith("\"") && normalizedTableName.endsWith("\"") && normalizedTableName.length() > 1) {
            normalizedTableName = normalizedTableName.substring(1, normalizedTableName.length() - 1);
        }

        // 如果包含点号，取最后一个点号后的部分作为表名
        int lastDotIndex = normalizedTableName.lastIndexOf('.');
        if (lastDotIndex >= 0 && lastDotIndex < normalizedTableName.length() - 1) {
            normalizedTableName = normalizedTableName.substring(lastDotIndex + 1);
        }

        if (IGNORE_TABLE_CASE) {
            return normalizedTableName.toLowerCase(Locale.ROOT);
        }

        return normalizedTableName;
    }

    /**
     * 检查是否已初始化
     *
     * @return true 如果已初始化，false 否则
     */
    public static boolean isInitialized() {
        return INITIALIZED.get();
    }

    /**
     * 获取配置管理器
     *
     * @return 配置管理器实例
     */
    public static DataSourceConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * 重置初始化状态（用于测试或配置热更新）
     */
    public static void reset() {
        INITIALIZED.set(false);
        configManager = null;
        IGNORE_TABLE_CASE = true;
        log.info("【securt-kit】ConfigInitializer reset completed");
    }

    /**
     * 是否忽略表名大小写
     *
     * @return true 表示忽略大小写
     */
    public static boolean isIgnoreTableCase() {
        return IGNORE_TABLE_CASE;
    }
}

