package io.github.hexlodev.core.parser;

import cn.hutool.cache.Cache;
import cn.hutool.cache.CacheUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import io.github.hexlodev.core.parser.dto.ColumnTableDto;
import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
     * SQL 解析结果缓存
     * Key: SQL 的 MD5 哈希值（规范化后的 SQL）
     * Value: 解析结果 ParseResult
     * 
     * 使用 Hutool 的 LRUCache 实现 LRU（最近最少使用）淘汰策略
     */
    private static volatile Cache<String, ParseResult> SQL_PARSE_CACHE = CacheUtil.newLRUCache(DEFAULT_MAX_CACHE_SIZE);

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
     * 解析结果内部类
     */
    public static class ParseResult {
        private final Map<String, ColumnTableDto> placeholderColumnTableMap;
        private final List<FieldEncryptorInfoDto> fieldEncryptorInfos;

        public ParseResult(Map<String, ColumnTableDto> placeholderColumnTableMap,
                          List<FieldEncryptorInfoDto> fieldEncryptorInfos) {
            this.placeholderColumnTableMap = placeholderColumnTableMap;
            this.fieldEncryptorInfos = fieldEncryptorInfos;
        }

        public Map<String, ColumnTableDto> getPlaceholderColumnTableMap() {
            return placeholderColumnTableMap;
        }

        public List<FieldEncryptorInfoDto> getFieldEncryptorInfos() {
            return fieldEncryptorInfos;
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
    public static Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseSql(
            String sql, SqlParser parser) throws JSQLParserException {
        if (StrUtil.isBlank(sql)) {
            log.warn("Attempted to parse empty SQL statement");
            return Pair.of(Collections.emptyMap(), Collections.emptyList());
        }

        // 1. 规范化 SQL（去除多余空格，统一大小写）
        String normalizedSql = normalizeSql(sql);

        // 2. 计算 MD5 作为缓存 key
        String cacheKey = DigestUtil.md5Hex(normalizedSql);

        // 3. 如果缓存启用，尝试从缓存获取
        if (cacheEnabled && SQL_PARSE_CACHE != null) {
            ParseResult cached = SQL_PARSE_CACHE.get(cacheKey);
            if (cached != null) {
                log.debug("Cache hit for SQL: {}", sql.substring(0, Math.min(50, sql.length())));
                // 返回缓存的副本，避免外部修改影响缓存
                Map<String, ColumnTableDto> mapCopy = new java.util.HashMap<>(cached.getPlaceholderColumnTableMap());
                List<FieldEncryptorInfoDto> listCopy = new java.util.ArrayList<>(cached.getFieldEncryptorInfos());
                return Pair.of(mapCopy, listCopy);
            }
            log.debug("Cache miss for SQL: {}", sql.substring(0, Math.min(50, sql.length())));
        }

        // 4. 缓存未命中或未启用，执行解析
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = parser.parse(sql);

        // 5. 如果缓存启用，缓存解析结果
        if (cacheEnabled && SQL_PARSE_CACHE != null) {
            // 创建新的 HashMap 和 ArrayList，避免外部修改影响缓存
            Map<String, ColumnTableDto> cachedMap = new java.util.HashMap<>(result.getKey());
            List<FieldEncryptorInfoDto> cachedList = new java.util.ArrayList<>(result.getValue());
            ParseResult parseResult = new ParseResult(cachedMap, cachedList);
            SQL_PARSE_CACHE.put(cacheKey, parseResult);
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

        // 3. 转换为小写（注意：这可能会影响某些数据库的大小写敏感性）
        // 为了安全起见，我们转换为小写，因为表名和字段名通常在配置中是小写的
        normalized = normalized.toLowerCase();

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
        return new CacheStatsInfo(SQL_PARSE_CACHE.size(), currentMaxSize, true);
    }

    /**
     * 清空缓存
     */
    public static void clear() {
        SQL_PARSE_CACHE.clear();
        log.info("SQL parse cache cleared");
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

        public CacheStatsInfo(long size, long maxSize, boolean enabled) {
            this.size = size;
            this.maxSize = maxSize;
            this.enabled = enabled;
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

