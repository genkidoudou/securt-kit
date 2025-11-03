package io.github.hexlodev.core;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.StrUtil;
import io.github.hexlodev.core.cache.StrategyCache;
import io.github.hexlodev.core.config.FieldEncryptorProperties;
import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class TableCache {
    /**
     * 表字段加密信息缓存
     * key: 表名（小写）
     * value: Map<字段名（小写）, FieldEncryptor注解>
     */
    private static final Map<String, Map<String, Class<? extends FieldEncryptorStrategy>>> TABLE_FIELD_ENCRYPT_INFO = new ConcurrentHashMap<>();


    /**
     * 需要加密的表名集合（小写）
     */
    private static final Set<String> FIELD_ENCRYPT_TABLE = new HashSet<>();


    /**
     * 是否初始化
     *
     * @author luyanan
     * @since 2025/10/9
     */

    private static final boolean init = false;

    /**
     * 初始化：先扫描实体（注解），再与配置文件合并
     * 注解优先：同表出现时跳过配置写入
     */
    public static void init(FieldEncryptorProperties fieldEncryptorProperties) {
        log.debug("【securt-kit】配置文件表缓存开始初始化");
        if (null == fieldEncryptorProperties) {
            log.debug("fieldEncryptorProperties未配置,请先配置");
            return;
        }
        // 使用策略缓存获取默认策略实例
        FieldEncryptorStrategy defaultFieldEncryptorStrategy;
        try {
            // 尝试获取默认策略（通过接口类型）
            defaultFieldEncryptorStrategy = StrategyCache.getStrategy(FieldEncryptorStrategy.class);
        } catch (Exception e) {
            log.warn("Failed to get default strategy, will use strategy class name instead: {}", e.getMessage());
            // 如果无法获取默认策略，将在后续逻辑中使用策略类名
            defaultFieldEncryptorStrategy = null;
        }
        Map<String, Map<String, Class<? extends FieldEncryptorStrategy>>> parserEntityClass = new HashMap<>();


        // 跟配置文件中的进行合并，
        List<FieldEncryptorProperties.TableConfig> tables = fieldEncryptorProperties.getTables();
        if (CollectionUtil.isNotEmpty(tables)) {
            for (FieldEncryptorProperties.TableConfig table : tables) {
                Map<String, Class<? extends FieldEncryptorStrategy>> fieldEncryptorMap = new HashMap<>();
                String tableName = table.getTableName().toLowerCase(Locale.ROOT);
                List<FieldEncryptorProperties.FieldConfig> fields = table.getFields();
                if (CollectionUtil.isNotEmpty(fields)) {
//                    if (parserEntityClass.containsKey(tableName)) {
//                        // 以添加了注解的为准
//                        continue;
//                    }
                    for (FieldEncryptorProperties.FieldConfig field : fields) {
                        String fieldName = field.getFieldName();
                        String strategy = field.getStrategy();
                        if (StrUtil.isBlank(strategy)) {
                            // 如果没有配置策略，使用默认策略的类名
                            if (defaultFieldEncryptorStrategy != null) {
                                strategy = defaultFieldEncryptorStrategy.getClass().getName();
                            } else {
                                log.warn("No default strategy available and no strategy configured for field: {}.{}", tableName, fieldName);
                                continue; // 跳过该字段
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

        // 初始化 SQL 解析缓存配置
        initSqlParseCache(fieldEncryptorProperties);

        log.debug("【securt-kit】配置文件表缓存初始化完成，需处理的表为:{}", TABLE_FIELD_ENCRYPT_INFO);
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
     * 获取加密的表名
     *
     * @return
     * @since 2025/10/9
     */
    public static Set<String> getTables() {
        return FIELD_ENCRYPT_TABLE;
    }


    /**
     * 是否包含加密的表
     *
     * @param tableName 表名
     * @return
     * @since 2025/10/10
     */
    public static boolean concatTable(String tableName) {
        return getTables().contains(tableName.toLowerCase());
    }


    public static boolean isInit() {
        return init;
    }

    /**
     * 获取表加密的字段
     *
     * @param tableName 表名
     * @return
     * @since 2025/10/10
     */
    public static Map<String, Class<? extends FieldEncryptorStrategy>> getTableFieldEncryptInfo(String tableName) {
        return TABLE_FIELD_ENCRYPT_INFO.get(tableName.toLowerCase());
    }


    /**
     * 根据表名获取加密的字段
     * @since 2025/10/12
     * @param tableName  表名
     * @return
     */
    public static List<String> getTableFieldName(String tableName) {
        Map<String, Class<? extends FieldEncryptorStrategy>> stringClassMap =
                TABLE_FIELD_ENCRYPT_INFO.get(tableName.toLowerCase());
        if (null == stringClassMap) {
            return null;
        }
        return new ArrayList<>(stringClassMap.keySet());
    }


    /**
     * 获取表加密的字段
     *
     * @param tableName 表名
     * @return
     * @since 2025/10/10
     */
    public static Class<? extends FieldEncryptorStrategy> getTableFieldEncryptInfo(String tableName, String fieldName) {
        if (StrUtil.isBlank(tableName) || StrUtil.isBlank(fieldName)) {
            return null;
        }
        Map<String, Class<? extends FieldEncryptorStrategy>> stringClassMap =
                TABLE_FIELD_ENCRYPT_INFO.get(tableName.toLowerCase());
        if (null == stringClassMap) {
            return null;
        }
        return stringClassMap.get(fieldName);
    }

}
