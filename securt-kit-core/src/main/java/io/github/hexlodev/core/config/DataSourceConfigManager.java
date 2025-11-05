package io.github.hexlodev.core.config;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源配置管理器
 * <p>
 * 职责：
 * 1. 管理所有数据源的配置
 * 2. 提供配置查找接口（根据数据源标识）
 * 3. 处理配置继承和覆盖逻辑
 * </p>
 * <p>
 * 配置合并规则：
 * <ul>
 *   <li>enable: 数据源配置优先，如果为 null 则继承全局配置</li>
 *   <li>failurePolicy: 数据源配置优先，如果为 null 则继承全局配置</li>
 *   <li>sqlParseCache: 数据源配置优先，如果为 null 则继承全局配置</li>
 *   <li>tables: 数据源配置覆盖全局配置（按表名合并）</li>
 * </ul>
 * </p>
 *
 * @author hexlodev
 * @since 1.1.0
 */
@Slf4j
public class DataSourceConfigManager {

    /**
     * 默认数据源标识
     */
    public static final String DEFAULT_DATASOURCE_ID = "default";

    /**
     * 全局配置（旧配置方式使用）
     */
    private MergedConfig globalMergedConfig;

    /**
     * 数据源配置映射（旧配置方式使用）
     * Key: 数据源标识
     * Value: 数据源配置
     */
    private Map<String, FieldEncryptorProperties.DataSourceConfig> datasourceConfigs;

    /**
     * 数据源配置缓存（合并后的最终配置）
     * Key: 数据源标识
     * Value: 合并后的配置
     */
    private final Map<String, MergedConfig> mergedConfigCache = new ConcurrentHashMap<>();

    /**
     * 构造函数
     * <p>
     * 支持两种配置方式：
     * 1. 新方式：在 tables 中直接指定 datasource-id（推荐）
     * 2. 旧方式：使用 global + datasources 结构（向后兼容）
     * </p>
     *
     * @param properties 配置属性
     */
    public DataSourceConfigManager(FieldEncryptorProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("FieldEncryptorProperties cannot be null");
        }

        // 判断使用哪种配置方式
        boolean useNewConfigStyle = isUsingNewConfigStyle(properties);
        
        if (useNewConfigStyle) {
            // 新方式：在 tables 中直接指定 datasource-id
            log.debug("Using new config style: datasource-id in TableConfig");
            buildConfigFromTables(properties);
        } else {
            // 旧方式：使用 global + datasources 结构（向后兼容）
            log.debug("Using legacy config style: global + datasources");
            buildConfigFromGlobalAndDatasources(properties);
        }

        log.info("DataSourceConfigManager initialized with {} datasource configurations", 
                mergedConfigCache.size());
    }

    /**
     * 判断是否使用新的配置方式（在 tables 中直接指定 datasource-id）
     */
    private boolean isUsingNewConfigStyle(FieldEncryptorProperties properties) {
        // 如果配置了 global 或 datasources，则认为是旧配置方式
        if (properties.getGlobal() != null || 
            (properties.getDatasources() != null && !properties.getDatasources().isEmpty())) {
            return false;
        }
        
        // 如果 tables 中有任何表配置指定了 datasourceId，则认为是新配置方式
        if (CollectionUtil.isNotEmpty(properties.getTables())) {
            for (FieldEncryptorProperties.TableConfig table : properties.getTables()) {
                if (StrUtil.isNotBlank(table.getDatasourceId())) {
                    return true;
                }
            }
        }
        
        // 默认使用新配置方式（单数据源场景，tables 中没有 datasourceId）
        return true;
    }

    /**
     * 从 tables 配置构建（新方式）
     */
    private void buildConfigFromTables(FieldEncryptorProperties properties) {
        // 按 datasourceId 分组 tables
        Map<String, List<FieldEncryptorProperties.TableConfig>> tablesByDatasource = new HashMap<>();
        
        if (CollectionUtil.isNotEmpty(properties.getTables())) {
            for (FieldEncryptorProperties.TableConfig table : properties.getTables()) {
                String datasourceId = StrUtil.isBlank(table.getDatasourceId()) 
                    ? DEFAULT_DATASOURCE_ID 
                    : table.getDatasourceId();
                
                tablesByDatasource.computeIfAbsent(datasourceId, k -> new ArrayList<>()).add(table);
            }
        }
        
        // 为每个数据源构建配置
        for (Map.Entry<String, List<FieldEncryptorProperties.TableConfig>> entry : tablesByDatasource.entrySet()) {
            String datasourceId = entry.getKey();
            MergedConfig merged = new MergedConfig();
            merged.setEnable(properties.isEnable());
            merged.setFailurePolicy(properties.getFailurePolicy());
            merged.setSqlParseCache(properties.getSqlParseCache());
            merged.setTables(entry.getValue());
            mergedConfigCache.put(datasourceId, merged);
        }
        
        // 确保默认数据源存在
        if (!mergedConfigCache.containsKey(DEFAULT_DATASOURCE_ID)) {
            MergedConfig defaultConfig = new MergedConfig();
            defaultConfig.setEnable(properties.isEnable());
            defaultConfig.setFailurePolicy(properties.getFailurePolicy());
            defaultConfig.setSqlParseCache(properties.getSqlParseCache());
            defaultConfig.setTables(new ArrayList<>());
            mergedConfigCache.put(DEFAULT_DATASOURCE_ID, defaultConfig);
        }
    }

    /**
     * 从 global + datasources 配置构建（旧方式，向后兼容）
     */
    private void buildConfigFromGlobalAndDatasources(FieldEncryptorProperties properties) {
        // 构建全局配置
        this.globalMergedConfig = buildGlobalMergedConfig(properties);

        // 初始化数据源配置映射
        this.datasourceConfigs = new HashMap<>();
        if (properties.getDatasources() != null) {
            this.datasourceConfigs.putAll(properties.getDatasources());
        }
    }

    /**
     * 构建全局合并配置（向后兼容）
     * <p>
     * 如果配置了 global，则使用 global 配置；
     * 否则使用顶级配置（向后兼容单数据源场景）。
     * </p>
     */
    private MergedConfig buildGlobalMergedConfig(FieldEncryptorProperties properties) {
        MergedConfig merged = new MergedConfig();

        // 判断是否使用了多数据源配置结构
        if (properties.getGlobal() != null || 
            (properties.getDatasources() != null && !properties.getDatasources().isEmpty())) {
            // 使用 global 配置
            FieldEncryptorProperties.GlobalConfig global = properties.getGlobal();
            if (global != null) {
                merged.setEnable(global.getEnable() != null ? global.getEnable() : properties.isEnable());
                merged.setFailurePolicy(global.getFailurePolicy() != null ? 
                    global.getFailurePolicy() : properties.getFailurePolicy());
                merged.setSqlParseCache(global.getSqlParseCache() != null ? 
                    global.getSqlParseCache() : properties.getSqlParseCache());
                merged.setTables(global.getTables() != null ? global.getTables() : properties.getTables());
            } else {
                // 没有 global 配置，使用顶级配置作为默认值
                merged.setEnable(properties.isEnable());
                merged.setFailurePolicy(properties.getFailurePolicy());
                merged.setSqlParseCache(properties.getSqlParseCache());
                merged.setTables(properties.getTables());
            }
        } else {
            // 向后兼容：使用顶级配置
            merged.setEnable(properties.isEnable());
            merged.setFailurePolicy(properties.getFailurePolicy());
            merged.setSqlParseCache(properties.getSqlParseCache());
            merged.setTables(properties.getTables());
        }

        return merged;
    }

    /**
     * 根据数据源标识获取配置
     *
     * @param datasourceId 数据源标识，如果为 null 或 "default" 则返回默认配置
     * @return 合并后的配置
     */
    public MergedConfig getConfig(String datasourceId) {
        String dsId = StrUtil.isBlank(datasourceId) ? DEFAULT_DATASOURCE_ID : datasourceId;
        
        // 如果使用新配置方式，直接从缓存获取
        if (globalMergedConfig == null) {
            return mergedConfigCache.getOrDefault(dsId, getDefaultConfig());
        }
        
        // 旧配置方式：返回全局配置或合并配置
        if (DEFAULT_DATASOURCE_ID.equals(dsId)) {
            return globalMergedConfig;
        }

        // 从缓存中获取或计算
        return mergedConfigCache.computeIfAbsent(dsId, this::mergeConfig);
    }

    /**
     * 获取默认配置
     */
    private MergedConfig getDefaultConfig() {
        MergedConfig defaultConfig = new MergedConfig();
        defaultConfig.setEnable(true);
        defaultConfig.setFailurePolicy(FieldEncryptorProperties.FailurePolicy.FALLBACK);
        defaultConfig.setTables(new ArrayList<>());
        return defaultConfig;
    }

    /**
     * 合并配置（数据源配置覆盖全局配置）
     *
     * @param datasourceId 数据源标识
     * @return 合并后的配置
     */
    private MergedConfig mergeConfig(String datasourceId) {
        FieldEncryptorProperties.DataSourceConfig dsConfig = datasourceConfigs.get(datasourceId);

        if (dsConfig == null) {
            // 如果没有数据源级别配置，返回全局配置
            log.debug("No datasource-specific config found for '{}', using global config", datasourceId);
            return globalMergedConfig;
        }

        // 合并逻辑
        MergedConfig merged = new MergedConfig();

        // 1. enable: 数据源配置优先，如果为 null 则继承全局
        merged.setEnable(dsConfig.getEnable() != null ? 
            dsConfig.getEnable() : globalMergedConfig.getEnable());

        // 2. failurePolicy: 数据源配置优先，如果为 null 则继承全局
        merged.setFailurePolicy(dsConfig.getFailurePolicy() != null ? 
            dsConfig.getFailurePolicy() : globalMergedConfig.getFailurePolicy());

        // 3. sqlParseCache: 数据源配置优先，如果为 null 则继承全局
        merged.setSqlParseCache(dsConfig.getSqlParseCache() != null ? 
            dsConfig.getSqlParseCache() : globalMergedConfig.getSqlParseCache());

        // 4. tables: 数据源配置覆盖全局配置（表名匹配）
        Map<String, FieldEncryptorProperties.TableConfig> tableMap = new HashMap<>();
        
        // 先添加全局配置的表
        if (CollectionUtil.isNotEmpty(globalMergedConfig.getTables())) {
            for (FieldEncryptorProperties.TableConfig table : globalMergedConfig.getTables()) {
                String tableName = table.getTableName().toLowerCase();
                tableMap.put(tableName, table);
            }
        }
        
        // 再覆盖数据源配置的表（数据源配置优先）
        if (CollectionUtil.isNotEmpty(dsConfig.getTables())) {
            for (FieldEncryptorProperties.TableConfig table : dsConfig.getTables()) {
                String tableName = table.getTableName().toLowerCase();
                tableMap.put(tableName, table);
            }
        }

        merged.setTables(new ArrayList<>(tableMap.values()));

        log.debug("Merged config for datasource '{}': enable={}, tables={}", 
                datasourceId, merged.getEnable(), 
                merged.getTables() != null ? merged.getTables().size() : 0);

        return merged;
    }

    /**
     * 获取所有数据源标识
     *
     * @return 数据源标识集合
     */
    public Set<String> getDatasourceIds() {
        Set<String> ids = new HashSet<>();
        if (globalMergedConfig == null) {
            // 新配置方式：从缓存中获取所有数据源ID
            ids.addAll(mergedConfigCache.keySet());
        } else {
            // 旧配置方式：从 datasourceConfigs 中获取
            ids.addAll(datasourceConfigs.keySet());
            ids.add(DEFAULT_DATASOURCE_ID);
        }
        return ids;
    }

    /**
     * 合并后的配置对象
     * <p>
     * 包含所有配置项的最终值（已合并全局和数据源配置）
     * </p>
     */
    @Data
    public static class MergedConfig {
        /**
         * 是否启用加密
         */
        private Boolean enable;

        /**
         * 失败策略
         */
        private FieldEncryptorProperties.FailurePolicy failurePolicy;

        /**
         * SQL 解析缓存配置
         */
        private FieldEncryptorProperties.SqlParseCacheConfig sqlParseCache;

        /**
         * 表配置列表（已合并）
         */
        private List<FieldEncryptorProperties.TableConfig> tables;
    }
}

