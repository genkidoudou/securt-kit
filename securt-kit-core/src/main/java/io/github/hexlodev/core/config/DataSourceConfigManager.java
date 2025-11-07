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
 * </p>
 * <p>
 * 配置方式：
 * 在 tables 中通过 datasource-id 区分不同数据源的配置
 * - 不指定 datasource-id：应用到所有数据源（单数据源场景）
 * - 指定 datasource-id：只应用到该数据源（多数据源场景）
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
     * 数据源配置缓存
     * Key: 数据源标识
     * Value: 合并后的配置
     */
    private final Map<String, MergedConfig> configCache = new ConcurrentHashMap<>();

    /**
     * 构造函数
     *
     * @param properties 配置属性
     */
    public DataSourceConfigManager(FieldEncryptorProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("FieldEncryptorProperties cannot be null");
        }

        buildConfigFromTables(properties);

        log.info("DataSourceConfigManager initialized with {} datasource configurations", 
                configCache.size());
    }

    /**
     * 从 tables 配置构建
     */
    private void buildConfigFromTables(FieldEncryptorProperties properties) {
        // 按 datasourceId 分组 tables
        Map<String, List<FieldEncryptorProperties.TableConfig>> tablesByDatasource = new HashMap<>();
        boolean hasDefaultConfig = false; // 标记是否有未指定 datasource-id 的配置
        
        if (CollectionUtil.isNotEmpty(properties.getTables())) {
            for (FieldEncryptorProperties.TableConfig table : properties.getTables()) {
                String datasourceId = table.getDatasourceId();
                
                // 如果未指定 datasource-id，则使用 default
                if (StrUtil.isBlank(datasourceId)) {
                    datasourceId = DEFAULT_DATASOURCE_ID;
                    hasDefaultConfig = true;
                }
                
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
            configCache.put(datasourceId, merged);
        }
        
        // 只有在以下情况才添加 default 数据源：
        // 1. 有未指定 datasource-id 的配置（单数据源场景）
        // 2. 或者没有任何配置（空配置场景）
        // 如果所有配置都指定了 datasource-id（多数据源场景），则不添加 default
        if (hasDefaultConfig || configCache.isEmpty()) {
            if (!configCache.containsKey(DEFAULT_DATASOURCE_ID)) {
                MergedConfig defaultConfig = new MergedConfig();
                defaultConfig.setEnable(properties.isEnable());
                defaultConfig.setFailurePolicy(properties.getFailurePolicy());
                defaultConfig.setSqlParseCache(properties.getSqlParseCache());
                defaultConfig.setTables(new ArrayList<>());
                configCache.put(DEFAULT_DATASOURCE_ID, defaultConfig);
            }
        }
    }

    /**
     * 根据数据源标识获取配置
     *
     * @param datasourceId 数据源标识，如果为 null 或 "default" 则返回默认配置
     * @return 合并后的配置
     */
    public MergedConfig getConfig(String datasourceId) {
        String dsId = StrUtil.isBlank(datasourceId) ? DEFAULT_DATASOURCE_ID : datasourceId;
        return configCache.getOrDefault(dsId, getDefaultConfig());
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
     * 获取所有数据源标识
     *
     * @return 数据源标识集合
     */
    public Set<String> getDatasourceIds() {
        return new HashSet<>(configCache.keySet());
    }

    /**
     * 合并后的配置对象
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

