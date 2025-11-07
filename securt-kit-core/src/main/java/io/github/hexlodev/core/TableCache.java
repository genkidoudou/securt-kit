package io.github.hexlodev.core;

import cn.hutool.core.util.StrUtil;
import io.github.hexlodev.core.config.ConfigInitializer;
import io.github.hexlodev.core.config.FieldEncryptorProperties;
import io.github.hexlodev.core.config.TableConfigRegistry;
import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 表缓存门面类
 * <p>
 * 提供统一的对外接口，委托给 {@link ConfigInitializer} 和 {@link TableConfigRegistry}
 * </p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Slf4j
public class TableCache {

    /**
     * 初始化：先扫描实体（注解），再与配置文件合并
     * 注解优先：同表出现时跳过配置写入
     * <p>
     * 使用原子操作保证线程安全，防止重复初始化
     * </p>
     *
     * @param fieldEncryptorProperties 字段加密配置属性，不能为 null
     * @throws IllegalArgumentException 如果配置无效
     * @throws RuntimeException 如果初始化失败
     */
    public static void init(FieldEncryptorProperties fieldEncryptorProperties) {
        ConfigInitializer.initialize(fieldEncryptorProperties);
    }

    /**
     * 检查表是否需要加密（向后兼容，使用默认数据源）
     *
     * @param tableName 表名，不能为 null 或空白，可能包含数据库名和schema前缀
     * @return 如果表需要加密返回 true，否则返回 false
     * @throws IllegalArgumentException 如果表名为 null 或空白
     */
    public static boolean concatTable(String tableName) {
        return concatTable(tableName, null);
    }

    /**
     * 检查表是否需要加密（支持数据源标识）
     *
     * @param tableName 表名，不能为 null 或空白，可能包含数据库名和schema前缀
     * @param datasourceId 数据源标识，如果为 null 则使用默认数据源
     * @return 如果表需要加密返回 true，否则返回 false
     * @throws IllegalArgumentException 如果表名为 null 或空白
     */
    public static boolean concatTable(String tableName, String datasourceId) {
        if (StrUtil.isBlank(tableName)) {
            throw new IllegalArgumentException("Table name cannot be null or blank");
        }
        String pureTableName = ConfigInitializer.extractPureTableName(tableName);
        return TableConfigRegistry.isTableEncrypted(pureTableName, datasourceId);
    }

    /**
     * 获取表加密的字段（向后兼容，使用默认数据源）
     *
     * @param tableName 表名，不能为 null 或空白，可能包含数据库名和schema前缀
     * @return 字段加密策略映射，如果表不存在或参数无效则返回 null
     * @throws IllegalArgumentException 如果表名为 null 或空白
     */
    public static Map<String, Class<? extends FieldEncryptorStrategy>> getTableFieldEncryptInfo(String tableName) {
        return getTableFieldEncryptInfo(tableName, null);
    }

    /**
     * 获取表加密的字段（支持数据源标识）
     *
     * @param tableName 表名，不能为 null 或空白，可能包含数据库名和schema前缀
     * @param datasourceId 数据源标识，如果为 null 则使用默认数据源
     * @return 字段加密策略映射，如果表不存在或参数无效则返回 null
     * @throws IllegalArgumentException 如果表名为 null 或空白
     */
    public static Map<String, Class<? extends FieldEncryptorStrategy>> getTableFieldEncryptInfo(
            String tableName, String datasourceId) {
        if (StrUtil.isBlank(tableName)) {
            throw new IllegalArgumentException("Table name cannot be null or blank");
        }
        String pureTableName = ConfigInitializer.extractPureTableName(tableName);
        return TableConfigRegistry.getTableFields(pureTableName, datasourceId);
    }

    /**
     * 根据表名获取加密的字段名列表
     *
     * @param tableName 表名，不能为 null 或空白，可能包含数据库名和schema前缀
     * @return 加密字段名列表，如果表不存在或参数无效则返回 null
     * @throws IllegalArgumentException 如果表名为 null 或空白
     */
    public static List<String> getTableFieldName(String tableName) {
        if (StrUtil.isBlank(tableName)) {
            throw new IllegalArgumentException("Table name cannot be null or blank");
        }
        String pureTableName = ConfigInitializer.extractPureTableName(tableName);
        return TableConfigRegistry.getTableFieldNames(pureTableName);
    }

    /**
     * 获取表字段的加密策略（向后兼容，使用默认数据源）
     *
     * @param tableName 表名，不能为 null 或空白，可能包含数据库名和schema前缀
     * @param fieldName 字段名，不能为 null 或空白
     * @return 加密策略类，如果表或字段不存在或参数无效则返回 null
     * @throws IllegalArgumentException 如果表名或字段名为 null 或空白
     */
    public static Class<? extends FieldEncryptorStrategy> getTableFieldEncryptStrategy(String tableName, String fieldName) {
        return getTableFieldEncryptStrategy(tableName, fieldName, null);
    }

    /**
     * 获取表字段的加密策略（支持数据源标识）
     *
     * @param tableName 表名，不能为 null 或空白，可能包含数据库名和schema前缀
     * @param fieldName 字段名，不能为 null 或空白
     * @param datasourceId 数据源标识，如果为 null 则使用默认数据源
     * @return 加密策略类，如果表或字段不存在或参数无效则返回 null
     * @throws IllegalArgumentException 如果表名或字段名为 null 或空白
     */
    public static Class<? extends FieldEncryptorStrategy> getTableFieldEncryptStrategy(
            String tableName, String fieldName, String datasourceId) {
        if (StrUtil.isBlank(tableName)) {
            throw new IllegalArgumentException("Table name cannot be null or blank");
        }
        if (StrUtil.isBlank(fieldName)) {
            throw new IllegalArgumentException("Field name cannot be null or blank");
        }
        String pureTableName = ConfigInitializer.extractPureTableName(tableName);
        return TableConfigRegistry.getFieldStrategy(pureTableName, fieldName, datasourceId);
    }

    /**
     * 获取加密的表名集合
     *
     * @return 加密表名集合的不可变视图
     */
    public static Set<String> getTables() {
        return TableConfigRegistry.getEncryptedTables();
    }

    /**
     * 检查是否已初始化
     *
     * @return true 如果已初始化，false 否则
     */
    public static boolean isInit() {
        return ConfigInitializer.isInitialized();
    }

    /**
     * 重置初始化状态（用于测试或配置热更新）
     */
    public static void reset() {
        ConfigInitializer.reset();
        TableConfigRegistry.clear();
        log.info("【securt-kit】TableCache reset completed");
    }
}
