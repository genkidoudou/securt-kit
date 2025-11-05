package io.github.hexlodev.core;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.StrUtil;
import io.github.hexlodev.core.cache.StrategyCache;
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
     * 表字段加密信息缓存
     * key: 表名（小写）
     * value: Map<字段名（小写）, FieldEncryptor注解>
     */
    private static final Map<String, Map<String, Class<? extends FieldEncryptorStrategy>>> TABLE_FIELD_ENCRYPT_INFO = new ConcurrentHashMap<>();


    /**
     * 需要加密的表名集合（小写）
     * <p>
     * 使用 ConcurrentHashMap.newKeySet() 保证线程安全，性能优于 Collections.synchronizedSet()
     * </p>
     */
    private static final Set<String> FIELD_ENCRYPT_TABLE = ConcurrentHashMap.newKeySet();


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
            
            // 验证配置
            validateConfiguration(fieldEncryptorProperties);
            
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

            // 初始化异常处理策略
            EncryptionHandler.initFromConfig(fieldEncryptorProperties);

            log.debug("【securt-kit】配置文件表缓存初始化完成，需处理的表为:{}", TABLE_FIELD_ENCRYPT_INFO);
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
     * 从表名中提取纯表名（去掉数据库名和schema前缀）
     * 
     * <p>支持的表名格式：</p>
     * <ul>
     *   <li>{@code database.table} -> {@code table}</li>
     *   <li>{@code schema.table} -> {@code table}</li>
     *   <li>{@code database.schema.table} -> {@code table}</li>
     *   <li>{@code table} -> {@code table}（已经是纯表名）</li>
     * </ul>
     *
     * @param tableName 表名，可能包含数据库名和schema前缀
     * @return 纯表名（小写），如果输入为空则返回原值
     */
    private static String extractPureTableName(String tableName) {
        if (StrUtil.isBlank(tableName)) {
            return tableName;
        }
        
        // 转换为小写
        String lowerTableName = tableName.toLowerCase().trim();
        
        // 如果包含点号，取最后一个点号后的部分作为表名
        int lastDotIndex = lowerTableName.lastIndexOf('.');
        if (lastDotIndex >= 0 && lastDotIndex < lowerTableName.length() - 1) {
            return lowerTableName.substring(lastDotIndex + 1);
        }
        
        // 如果没有点号，说明已经是纯表名
        return lowerTableName;
    }

    /**
     * 检查表是否需要加密
     * 
     * <p>支持带数据库名的表名格式（如 testdb.user），会自动提取纯表名进行匹配。</p>
     *
     * @param tableName 表名，不能为 null 或空白，可能包含数据库名和schema前缀
     * @return 如果表需要加密返回 true，否则返回 false
     * @throws IllegalArgumentException 如果表名为 null 或空白
     * @since 2025/10/10
     */
    public static boolean concatTable(String tableName) {
        if (StrUtil.isBlank(tableName)) {
            throw new IllegalArgumentException("Table name cannot be null or blank");
        }
        // 提取纯表名进行匹配
        String pureTableName = extractPureTableName(tableName);
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
        log.info("【securt-kit】TableCache reset completed");
    }

    /**
     * 获取表加密的字段
     * 
     * <p>支持带数据库名的表名格式（如 testdb.user），会自动提取纯表名进行查找。</p>
     *
     * @param tableName 表名，不能为 null 或空白，可能包含数据库名和schema前缀
     * @return 字段加密策略映射，如果表不存在或参数无效则返回 null
     * @throws IllegalArgumentException 如果表名为 null 或空白
     * @since 2025/10/10
     */
    public static Map<String, Class<? extends FieldEncryptorStrategy>> getTableFieldEncryptInfo(String tableName) {
        if (StrUtil.isBlank(tableName)) {
            throw new IllegalArgumentException("Table name cannot be null or blank");
        }
        // 提取纯表名进行查找
        String pureTableName = extractPureTableName(tableName);
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
     * 获取表字段的加密策略
     * 
     * <p>支持带数据库名的表名格式（如 testdb.user），会自动提取纯表名进行查找。</p>
     *
     * @param tableName 表名，不能为 null 或空白，可能包含数据库名和schema前缀
     * @param fieldName 字段名，不能为 null 或空白
     * @return 加密策略类，如果表或字段不存在或参数无效则返回 null
     * @throws IllegalArgumentException 如果表名或字段名为 null 或空白
     * @since 2025/10/10
     */
    public static Class<? extends FieldEncryptorStrategy> getTableFieldEncryptInfo(String tableName, String fieldName) {
        if (StrUtil.isBlank(tableName)) {
            throw new IllegalArgumentException("Table name cannot be null or blank");
        }
        if (StrUtil.isBlank(fieldName)) {
            throw new IllegalArgumentException("Field name cannot be null or blank");
        }
        // 提取纯表名进行查找
        String pureTableName = extractPureTableName(tableName);
        Map<String, Class<? extends FieldEncryptorStrategy>> stringClassMap =
                TABLE_FIELD_ENCRYPT_INFO.get(pureTableName);
        if (null == stringClassMap) {
            return null;
        }
        return stringClassMap.get(fieldName.toLowerCase());
    }

}
