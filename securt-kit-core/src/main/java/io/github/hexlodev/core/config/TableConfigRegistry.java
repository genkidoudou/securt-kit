package io.github.hexlodev.core.config;

import cn.hutool.core.util.StrUtil;
import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表配置注册表
 * <p>
 * 负责表配置的存储、查询、表名处理
 * </p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Slf4j
public class TableConfigRegistry {

    /**
     * 表字段加密信息缓存（向后兼容，单数据源场景）
     * key: 表名（小写）
     * value: Map<字段名（小写）, 策略类>
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
     * 注册表配置（单数据源）
     *
     * @param tableName 表名（纯表名，小写）
     * @param fieldMap  字段配置映射
     */
    public static void registerTable(String tableName, Map<String, Class<? extends FieldEncryptorStrategy>> fieldMap) {
        if (StrUtil.isBlank(tableName) || fieldMap == null || fieldMap.isEmpty()) {
            return;
        }
        TABLE_FIELD_ENCRYPT_INFO.put(tableName, fieldMap);
        FIELD_ENCRYPT_TABLE.add(tableName);
    }

    /**
     * 注册表配置（多数据源）
     *
     * @param datasourceId 数据源标识
     * @param tableName     表名（纯表名，小写）
     * @param fieldMap      字段配置映射
     */
    public static void registerTable(String datasourceId, String tableName, Map<String, Class<? extends FieldEncryptorStrategy>> fieldMap) {
        if (StrUtil.isBlank(datasourceId) || StrUtil.isBlank(tableName) || fieldMap == null || fieldMap.isEmpty()) {
            return;
        }

        // 注册到多数据源缓存
        DATASOURCE_TABLE_FIELD_ENCRYPT_INFO.computeIfAbsent(datasourceId, k -> new ConcurrentHashMap<>())
                .put(tableName, fieldMap);
        DATASOURCE_FIELD_ENCRYPT_TABLE.computeIfAbsent(datasourceId, k -> ConcurrentHashMap.newKeySet())
                .add(tableName);

        // 向后兼容：如果是默认数据源，同时更新单数据源缓存
        if (DataSourceConfigManager.DEFAULT_DATASOURCE_ID.equals(datasourceId)) {
            TABLE_FIELD_ENCRYPT_INFO.put(tableName, fieldMap);
            FIELD_ENCRYPT_TABLE.add(tableName);
        }
    }

    /**
     * 检查表是否需要加密（单数据源）
     *
     * @param tableName 表名（纯表名，小写）
     * @return 如果表需要加密返回 true，否则返回 false
     */
    public static boolean isTableEncrypted(String tableName) {
        if (StrUtil.isBlank(tableName)) {
            return false;
        }
        return FIELD_ENCRYPT_TABLE.contains(tableName);
    }

    /**
     * 检查表是否需要加密（多数据源）
     *
     * @param tableName     表名（纯表名，小写）
     * @param datasourceId 数据源标识
     * @return 如果表需要加密返回 true，否则返回 false
     */
    public static boolean isTableEncrypted(String tableName, String datasourceId) {
        if (StrUtil.isBlank(tableName)) {
            return false;
        }
        String dsId = StrUtil.isBlank(datasourceId) ? DataSourceConfigManager.DEFAULT_DATASOURCE_ID : datasourceId;

        // 优先使用多数据源缓存
        Set<String> encryptTables = DATASOURCE_FIELD_ENCRYPT_TABLE.get(dsId);
        if (encryptTables != null) {
            return encryptTables.contains(tableName);
        }

        // 向后兼容：使用单数据源缓存
        return FIELD_ENCRYPT_TABLE.contains(tableName);
    }

    /**
     * 获取表字段配置（单数据源）
     *
     * @param tableName 表名（纯表名，小写）
     * @return 字段配置映射，如果表不存在则返回 null
     */
    public static Map<String, Class<? extends FieldEncryptorStrategy>> getTableFields(String tableName) {
        if (StrUtil.isBlank(tableName)) {
            return null;
        }
        return TABLE_FIELD_ENCRYPT_INFO.get(tableName);
    }

    /**
     * 获取表字段配置（多数据源）
     *
     * @param tableName     表名（纯表名，小写）
     * @param datasourceId 数据源标识
     * @return 字段配置映射，如果表不存在则返回 null
     */
    public static Map<String, Class<? extends FieldEncryptorStrategy>> getTableFields(String tableName, String datasourceId) {
        if (StrUtil.isBlank(tableName)) {
            return null;
        }
        String dsId = StrUtil.isBlank(datasourceId) ? DataSourceConfigManager.DEFAULT_DATASOURCE_ID : datasourceId;

        // 优先使用多数据源缓存
        Map<String, Map<String, Class<? extends FieldEncryptorStrategy>>> datasourceConfig =
                DATASOURCE_TABLE_FIELD_ENCRYPT_INFO.get(dsId);
        if (datasourceConfig != null) {
            return datasourceConfig.get(tableName);
        }

        // 向后兼容：使用单数据源缓存
        return TABLE_FIELD_ENCRYPT_INFO.get(tableName);
    }

    /**
     * 获取表字段名列表
     *
     * @param tableName 表名（纯表名，小写）
     * @return 字段名列表，如果表不存在则返回 null
     */
    public static List<String> getTableFieldNames(String tableName) {
        if (StrUtil.isBlank(tableName)) {
            return null;
        }
        Map<String, Class<? extends FieldEncryptorStrategy>> fieldMap = TABLE_FIELD_ENCRYPT_INFO.get(tableName);
        if (fieldMap == null) {
            return null;
        }
        return new ArrayList<>(fieldMap.keySet());
    }

    /**
     * 获取字段加密策略（单数据源）
     *
     * @param tableName 表名（纯表名，小写）
     * @param fieldName 字段名（小写）
     * @return 加密策略类，如果表或字段不存在则返回 null
     */
    public static Class<? extends FieldEncryptorStrategy> getFieldStrategy(String tableName, String fieldName) {
        if (StrUtil.isBlank(tableName) || StrUtil.isBlank(fieldName)) {
            return null;
        }
        Map<String, Class<? extends FieldEncryptorStrategy>> fieldMap = TABLE_FIELD_ENCRYPT_INFO.get(tableName);
        if (fieldMap == null) {
            return null;
        }
        return fieldMap.get(fieldName.toLowerCase());
    }

    /**
     * 获取字段加密策略（多数据源）
     *
     * @param tableName     表名（纯表名，小写）
     * @param fieldName     字段名（小写）
     * @param datasourceId 数据源标识
     * @return 加密策略类，如果表或字段不存在则返回 null
     */
    public static Class<? extends FieldEncryptorStrategy> getFieldStrategy(String tableName, String fieldName, String datasourceId) {
        if (StrUtil.isBlank(tableName) || StrUtil.isBlank(fieldName)) {
            return null;
        }
        String dsId = StrUtil.isBlank(datasourceId) ? DataSourceConfigManager.DEFAULT_DATASOURCE_ID : datasourceId;

        // 优先使用多数据源缓存
        Map<String, Map<String, Class<? extends FieldEncryptorStrategy>>> datasourceConfig =
                DATASOURCE_TABLE_FIELD_ENCRYPT_INFO.get(dsId);
        if (datasourceConfig != null) {
            Map<String, Class<? extends FieldEncryptorStrategy>> tableConfig = datasourceConfig.get(tableName);
            if (tableConfig != null) {
                return tableConfig.get(fieldName.toLowerCase());
            }
        }

        // 向后兼容：使用单数据源缓存
        Map<String, Class<? extends FieldEncryptorStrategy>> fieldMap = TABLE_FIELD_ENCRYPT_INFO.get(tableName);
        if (fieldMap == null) {
            return null;
        }
        return fieldMap.get(fieldName.toLowerCase());
    }

    /**
     * 获取所有加密表名（单数据源）
     *
     * @return 加密表名集合的不可变视图
     */
    public static Set<String> getEncryptedTables() {
        return Collections.unmodifiableSet(FIELD_ENCRYPT_TABLE);
    }

    /**
     * 获取所有加密表名（多数据源）
     *
     * @param datasourceId 数据源标识
     * @return 加密表名集合的不可变视图，如果数据源不存在则返回空集合
     */
    public static Set<String> getEncryptedTables(String datasourceId) {
        if (StrUtil.isBlank(datasourceId)) {
            return getEncryptedTables();
        }
        Set<String> tables = DATASOURCE_FIELD_ENCRYPT_TABLE.get(datasourceId);
        if (tables != null) {
            return Collections.unmodifiableSet(tables);
        }
        return Collections.emptySet();
    }

    /**
     * 清空缓存（用于测试）
     */
    public static void clear() {
        TABLE_FIELD_ENCRYPT_INFO.clear();
        FIELD_ENCRYPT_TABLE.clear();
        DATASOURCE_TABLE_FIELD_ENCRYPT_INFO.clear();
        DATASOURCE_FIELD_ENCRYPT_TABLE.clear();
        log.info("【securt-kit】TableConfigRegistry cleared");
    }
}

