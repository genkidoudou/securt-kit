package io.github.genkidoudou.core.parser;

import cn.hutool.cache.Cache;
import cn.hutool.cache.CacheUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import io.github.genkidoudou.core.parser.dto.ColumnTableDto;
import io.github.genkidoudou.core.parser.dto.FieldEncryptorInfoDto;
import io.github.genkidoudou.core.utils.TableNameParser;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;

import java.util.*;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * SQL 解析结果缓存
 * <p>
 * 使用 Hutool 的缓存实现，避免重复解析相同的 SQL 语句。
 * 显著提升性能，特别是在高并发场景下。
 * </p>
 *
 * <p>缓存策略：</p>
 * <ul>
 *   <li>最大缓存 1000 条 SQL（LRU 淘汰策略）</li>
 *   <li>使用 LRU（最近最少使用）算法自动淘汰最久未使用的条目</li>
 * </ul>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Slf4j
public class SqlParseCache {

    /**
     * 缓存最大容量（默认值）
     */
    private static final int DEFAULT_MAX_CACHE_SIZE = 1000;

    /**
     * 是否启用缓存（默认启用）
     */
    private static volatile boolean cacheEnabled = true;

    /**
     * SQL 解析结果缓存（包含表名信息）
     * Key: SQL 的 MD5 哈希值（规范化后的 SQL）
     * Value: 解析结果 ParseResult（包含占位符映射、字段加密信息、表名集合）
     * 
     * 使用 Hutool 的 LRUCache 实现 LRU（最近最少使用）淘汰策略
     * 
     * 注意：此缓存同时服务于 SQL 完整解析和表名解析，合并缓存以减少内存占用
     */
    private static volatile Cache<String, ParseResult> SQL_PARSE_CACHE = CacheUtil.newLRUCache(DEFAULT_MAX_CACHE_SIZE);
    
    /**
     * 是否忽略表名大小写，默认忽略
     */
    private static volatile boolean ignoreTableCase = true;
    
    /**
     * 表名解析缓存（轻量级，用于快速判断是否需要加密）
     * Key: SQL 的 MD5 哈希值（规范化后的 SQL）
     * Value: 表名集合
     * 
     * 当 SQL 解析缓存未命中时，使用此缓存避免重复解析表名
     */
    private static volatile Cache<String, Set<String>> TABLE_NAME_CACHE = CacheUtil.newLRUCache(DEFAULT_MAX_CACHE_SIZE * 2);

    /**
     * 当前缓存最大容量
     */
    private static volatile int currentMaxSize = DEFAULT_MAX_CACHE_SIZE;

    /**
     * 初始化缓存配置
     *
     * @param enable 是否启用缓存
     * @param maxSize 缓存最大容量
     */
    public static void init(boolean enable, int maxSize) {
        cacheEnabled = enable;
        
        if (maxSize <= 0) {
            log.warn("Invalid cache maxSize: {}, using default: {}", maxSize, DEFAULT_MAX_CACHE_SIZE);
            maxSize = DEFAULT_MAX_CACHE_SIZE;
        }
        
            if (cacheEnabled) {
            // 如果容量改变，重新创建缓存
            if (currentMaxSize != maxSize || SQL_PARSE_CACHE == null) {
                synchronized (SqlParseCache.class) {
                    if (currentMaxSize != maxSize || SQL_PARSE_CACHE == null) {
                        Cache<String, ParseResult> oldCache = SQL_PARSE_CACHE;
                        SQL_PARSE_CACHE = CacheUtil.newLRUCache(maxSize);
                        // 表名缓存容量设为 SQL 解析缓存的 2 倍（表名解析使用频率更高）
                        TABLE_NAME_CACHE = CacheUtil.newLRUCache(maxSize * 2);
                        currentMaxSize = maxSize;
                        
                        // 如果旧缓存存在且有数据，可以选择迁移（这里简化处理，直接清空）
                        if (oldCache != null && oldCache.size() > 0) {
                            log.info("Cache reconfigured, cleared {} old entries", oldCache.size());
                        }
                        log.info("SQL parse cache initialized: enable={}, maxSize={}", enable, maxSize);
                    }
                }
            }
        } else {
            // 禁用缓存时清空
            if (SQL_PARSE_CACHE != null) {
                synchronized (SqlParseCache.class) {
                    SQL_PARSE_CACHE.clear();
                    TABLE_NAME_CACHE.clear();
                    log.info("SQL parse cache disabled");
                }
            }
        }
    }

    /**
     * 检查缓存是否启用
     *
     * @return true 如果缓存已启用
     */
    public static boolean isEnabled() {
        return cacheEnabled;
    }

    /**
     * 配置表名大小写是否忽略
     *
     * @param ignoreCase true 表示忽略大小写
     */
    public static void configureCaseSensitivity(boolean ignoreCase) {
        ignoreTableCase = ignoreCase;
        if (log.isDebugEnabled()) {
            log.debug("SQL parse cache configured to {} table case",
                    ignoreCase ? "ignore" : "respect");
        }
    }

    /**
     * 解析结果内部类
     */
    public static class ParseResult {
        private final Map<String, ColumnTableDto> placeholderColumnTableMap;
        private final List<FieldEncryptorInfoDto> fieldEncryptorInfos;
        private final Set<String> tableNames; // 表名集合（从解析结果中提取）

        public ParseResult(Map<String, ColumnTableDto> placeholderColumnTableMap,
                          List<FieldEncryptorInfoDto> fieldEncryptorInfos,
                          Set<String> tableNames) {
            this.placeholderColumnTableMap = placeholderColumnTableMap;
            this.fieldEncryptorInfos = fieldEncryptorInfos;
            this.tableNames = tableNames != null ? tableNames : Collections.emptySet();
        }

        public Map<String, ColumnTableDto> getPlaceholderColumnTableMap() {
            return placeholderColumnTableMap;
        }

        public List<FieldEncryptorInfoDto> getFieldEncryptorInfos() {
            return fieldEncryptorInfos;
        }

        public Set<String> getTableNames() {
            return tableNames;
        }
    }

    /**
     * 解析 SQL 并缓存结果
     * <p>
     * 如果缓存命中，直接返回缓存结果；否则解析 SQL 并缓存。
     * </p>
     *
     * @param sql 要解析的 SQL 语句
     * @param parser 实际的解析函数
     * @return 解析结果 Pair
     * @throws JSQLParserException 如果 SQL 解析失败
     */
    /**
     * 解析 SQL 并缓存结果
     * <p>
     * 如果缓存命中，直接返回缓存结果；否则解析 SQL 并缓存。
     * </p>
     *
     * @param sql 要解析的 SQL 语句，不能为 null 或空白
     * @param parser 实际的解析函数，不能为 null
     * @return 解析结果 Pair
     * @throws IllegalArgumentException 如果 SQL 为 null 或空白，或 parser 为 null
     * @throws JSQLParserException 如果 SQL 解析失败
     */
    public static Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseSql(
            String sql, SqlParser parser) throws JSQLParserException {
        if (StrUtil.isBlank(sql)) {
            throw new IllegalArgumentException("SQL statement cannot be null or blank");
        }
        if (parser == null) {
            throw new IllegalArgumentException("SQL parser cannot be null");
        }

        // 1. 规范化 SQL（去除多余空格，统一大小写）
        String normalizedSql = normalizeSql(sql);

        // 2. 计算 MD5 作为缓存 key
        String cacheKey = DigestUtil.md5Hex(normalizedSql);

        // 3. 如果缓存启用，尝试从缓存获取
        if (cacheEnabled && SQL_PARSE_CACHE != null) {
            ParseResult cached = SQL_PARSE_CACHE.get(cacheKey);
            if (cached != null) {
                log.debug("Cache hit for SQL: {}", sql);
                // 返回缓存的副本，避免外部修改影响缓存
                Map<String, ColumnTableDto> mapCopy = new java.util.HashMap<>(cached.getPlaceholderColumnTableMap());
                List<FieldEncryptorInfoDto> listCopy = new java.util.ArrayList<>(cached.getFieldEncryptorInfos());
                return Pair.of(mapCopy, listCopy);
            }
            log.debug("Cache miss for SQL: {}", sql);
        }

        // 4. 缓存未命中或未启用，执行解析
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = parser.parse(sql);

        // 5. 如果缓存启用，缓存解析结果（同时提取并缓存表名）
        if (cacheEnabled && SQL_PARSE_CACHE != null) {
            // 创建新的 HashMap 和 ArrayList，避免外部修改影响缓存
            Map<String, ColumnTableDto> cachedMap = new java.util.HashMap<>(result.getKey());
            List<FieldEncryptorInfoDto> cachedList = new java.util.ArrayList<>(result.getValue());
            
            // 从解析结果中提取表名集合
            Set<String> tableNames = extractTableNames(result.getKey(), result.getValue());
            
            ParseResult parseResult = new ParseResult(cachedMap, cachedList, tableNames);
            SQL_PARSE_CACHE.put(cacheKey, parseResult);
            
            // 同时缓存表名（用于快速路径）
            if (TABLE_NAME_CACHE != null && !tableNames.isEmpty()) {
                TABLE_NAME_CACHE.put(cacheKey, new HashSet<>(tableNames));
            }
        }

        return result;
    }

    /**
     * 规范化 SQL 语句
     * <p>
     * 规范化策略：
     * <ul>
     *   <li>去除首尾空格</li>
     *   <li>将多个连续空白字符替换为单个空格</li>
     *   <li>转换为小写（保留字符串字面量）</li>
     * </ul>
     * </p>
     *
     * @param sql 原始 SQL 语句
     * @return 规范化后的 SQL 语句
     */
    private static String normalizeSql(String sql) {
        if (StrUtil.isBlank(sql)) {
            return sql;
        }

        // 1. 去除首尾空格
        String normalized = sql.trim();

        // 2. 将多个连续空白字符替换为单个空格
        normalized = normalized.replaceAll("\\s+", " ");

        // 3. 根据配置决定是否统一转小写
        if (ignoreTableCase) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        }

        return normalized;
    }

    /**
     * 获取缓存统计信息（简化版，Hutool 缓存不提供详细的统计信息）
     *
     * @return 缓存统计信息对象
     */
    public static CacheStatsInfo getStats() {
        if (!cacheEnabled || SQL_PARSE_CACHE == null) {
            return new CacheStatsInfo(0, currentMaxSize, false);
        }
        long totalSize = SQL_PARSE_CACHE.size();
        long tableNameCacheSize = TABLE_NAME_CACHE != null ? TABLE_NAME_CACHE.size() : 0;
        return new CacheStatsInfo(totalSize, currentMaxSize, true, tableNameCacheSize);
    }

    /**
     * 从 SQL 解析结果中提取表名集合
     *
     * @param placeholderColumnTableMap 占位符到表字段的映射
     * @param fieldEncryptorInfos 字段加密信息列表
     * @return 表名集合（小写）
     */
    private static Set<String> extractTableNames(Map<String, ColumnTableDto> placeholderColumnTableMap,
                                                  List<FieldEncryptorInfoDto> fieldEncryptorInfos) {
        Set<String> tableNames = new HashSet<>();
        
        // 从占位符映射中提取表名
        if (CollectionUtil.isNotEmpty(placeholderColumnTableMap)) {
            for (ColumnTableDto dto : placeholderColumnTableMap.values()) {
                if (StrUtil.isNotBlank(dto.getSourceTableName())) {
                    tableNames.add(dto.getSourceTableName().toLowerCase());
                }
            }
        }
        
        // 从字段加密信息中提取表名
        if (CollectionUtil.isNotEmpty(fieldEncryptorInfos)) {
            for (FieldEncryptorInfoDto dto : fieldEncryptorInfos) {
                if (StrUtil.isNotBlank(dto.getSourceTableName())) {
                    tableNames.add(dto.getSourceTableName().toLowerCase());
                }
            }
        }
        
        return tableNames;
    }

    /**
     * 解析 SQL 中的表名（轻量级方法，优先使用缓存）
     * <p>
     * 该方法优先从 SQL 解析缓存中提取表名，如果缓存未命中，
     * 则使用轻量级的 TableNameParser 进行解析。
     * </p>
     *
     * @param sql SQL 语句
     * @return 表名集合（小写）
     */
    /**
     * 解析 SQL 中的表名（轻量级方法，优先使用缓存）
     * <p>
     * 该方法优先从 SQL 解析缓存中提取表名，如果缓存未命中，
     * 则使用轻量级的 TableNameParser 进行解析。
     * </p>
     *
     * @param sql SQL 语句，不能为 null 或空白
     * @return 表名集合（小写），如果 SQL 为 null 或空白则返回空集合
     * @throws IllegalArgumentException 如果 SQL 为 null 或空白
     */
    public static Set<String> parseTableNames(String sql) {
        if (sql == null) {
            throw new IllegalArgumentException("SQL statement cannot be null");
        }
        if (StrUtil.isBlank(sql)) {
            return Collections.emptySet();
        }

        // 1. 规范化 SQL
        String normalizedSql = normalizeSql(sql);
        String cacheKey = DigestUtil.md5Hex(normalizedSql);

        // 2. 优先从 SQL 解析缓存中提取表名
        if (cacheEnabled && SQL_PARSE_CACHE != null) {
            ParseResult cached = SQL_PARSE_CACHE.get(cacheKey);
            if (cached != null && CollectionUtil.isNotEmpty(cached.getTableNames())) {
                log.debug("Table names extracted from SQL parse cache");
                return new HashSet<>(cached.getTableNames());
            }
        }

        // 3. 从表名缓存中获取
        if (cacheEnabled && TABLE_NAME_CACHE != null) {
            Set<String> cachedTables = TABLE_NAME_CACHE.get(cacheKey);
            if (cachedTables != null && !cachedTables.isEmpty()) {
                log.debug("Table names found in table name cache");
                return new HashSet<>(cachedTables);
            }
        }

        // 4. 缓存未命中，使用轻量级解析器解析
        try {
            TableNameParser parser = new TableNameParser(sql);
            Set<String> tableNames = parser.tables();
            // 转换为小写并去重
            Set<String> normalizedTableNames = tableNames.stream()
                    .map(name -> {
                        if (name == null) {
                            return null;
                        }
                        String trimmed = name.trim();
                        return ignoreTableCase ? trimmed.toLowerCase(Locale.ROOT) : trimmed;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            
            // 缓存结果
            if (cacheEnabled && TABLE_NAME_CACHE != null) {
                TABLE_NAME_CACHE.put(cacheKey, normalizedTableNames);
            }
            
            return normalizedTableNames;
        } catch (Exception e) {
            log.warn("Failed to parse table names from SQL: {}", sql, e);
            return Collections.emptySet();
        }
    }

    /**
     * 清空缓存
     */
    public static void clear() {
        SQL_PARSE_CACHE.clear();
        TABLE_NAME_CACHE.clear();
        log.info("SQL parse cache and table name cache cleared");
    }

    /**
     * 获取当前缓存大小
     *
     * @return 缓存条目数量
     */
    public static long size() {
        if (!cacheEnabled || SQL_PARSE_CACHE == null) {
            return 0;
        }
        return SQL_PARSE_CACHE.size();
    }

    /**
     * 缓存统计信息（简化版）
     */
    public static class CacheStatsInfo {
        private final long size;
        private final long maxSize;
        private final boolean enabled;
        private final long tableNameCacheSize;

        public CacheStatsInfo(long size, long maxSize, boolean enabled) {
            this(size, maxSize, enabled, 0);
        }

        public CacheStatsInfo(long size, long maxSize, boolean enabled, long tableNameCacheSize) {
            this.size = size;
            this.maxSize = maxSize;
            this.enabled = enabled;
            this.tableNameCacheSize = tableNameCacheSize;
        }

        public long getSize() {
            return size;
        }

        public long getMaxSize() {
            return maxSize;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public long getTableNameCacheSize() {
            return tableNameCacheSize;
        }

        public double getUsageRate() {
            return maxSize > 0 ? (double) size / maxSize : 0.0;
        }
    }

    /**
     * SQL 解析器函数式接口
     */
    @FunctionalInterface
    public interface SqlParser {
        /**
         * 解析 SQL 语句
         *
         * @param sql SQL 语句
         * @return 解析结果
         * @throws JSQLParserException 解析异常
         */
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parse(String sql)
                throws JSQLParserException;
    }
}

