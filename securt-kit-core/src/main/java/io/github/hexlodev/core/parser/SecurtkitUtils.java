package io.github.hexlodev.core.parser;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import io.github.hexlodev.core.TableCache;
import io.github.hexlodev.core.parser.dto.ColumnTableDto;
import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
import io.github.hexlodev.core.parser.visitor.PoJoEncrtptorStatementVisitor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL解析工具类 - 核心解析入口
 *
 * <p>该类提供了SQL语句解析的核心功能，主要用于解析PreparedStatement中的SQL语句，
 * 识别需要加密的字段，并建立占位符与表字段的映射关系。</p>
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>将SQL中的问号占位符（?）替换为自定义占位符，以便进行SQL解析</li>
 *   <li>解析SQL语句，识别表名和字段名</li>
 *   <li>建立占位符索引与表字段的映射关系</li>
 *   <li>识别需要加密的字段列表</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * String sql = "UPDATE user SET name = ?, phone = ? WHERE id = ?";
 * Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = SecurtkitUtils.parseSql(sql);
 * // result.getKey() 包含占位符到表字段的映射
 * // result.getValue() 包含需要加密的字段信息
 * }</pre>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Slf4j
public class SecurtkitUtils {

    /** 日志记录器 */

    /**
     * 占位符前缀
     * 用于替换SQL中的问号占位符，便于SQL解析器识别
     */
    public static final String PLACEHOLDER = "SECURT_KIT_PLACEHOLDER_";

    /**
     * 占位符计数器（线程本地变量）
     * <p>
     * 使用 ThreadLocal 保证每个线程有独立的计数器，避免并发环境下计数错误。
     * 这样每个线程解析 SQL 时都能正确地从 1 开始计数。
     * </p>
     */
    private static final ThreadLocal<AtomicInteger> PLACEHOLDER_COUNTER = 
        ThreadLocal.withInitial(() -> new AtomicInteger(0));

    /**
     * 解析SQL语句，获取占位符与表字段的映射关系以及需要加密的字段列表
     * <p>
     * 该方法使用缓存机制，相同 SQL 的解析结果会被缓存，显著提升性能。
     * </p>
     *
     * <p>该方法会执行以下步骤：</p>
     * <ol>
     *   <li>检查缓存，如果命中则直接返回</li>
     *   <li>将SQL中的问号占位符替换为自定义占位符</li>
     *   <li>使用JSQLParser解析SQL语句</li>
     *   <li>通过访问者模式提取表字段信息和加密字段信息</li>
     *   <li>建立占位符索引与表字段的映射关系</li>
     *   <li>将解析结果存入缓存</li>
     * </ol>
     *
     * @param sql 要解析的SQL语句，包含问号占位符（如：UPDATE user SET name = ? WHERE id = ?）
     * @return Pair对象，包含：
     * <ul>
     *   <li>Key: 占位符到ColumnTableDto的映射（占位符名称 -> 表字段信息）</li>
     *   <li>Value: 需要加密的字段信息列表</li>
     * </ul>
     * @throws JSQLParserException 如果SQL解析失败
     * @since 1.0.0
     */
    public static Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseSql(String sql) throws JSQLParserException {
        // 使用缓存进行解析
        return SqlParseCache.parseSql(sql, SecurtkitUtils::doParseSql);
    }

    /**
     * 实际执行 SQL 解析的内部方法
     * <p>
     * 该方法不包含缓存逻辑，由 {@link #parseSql(String)} 通过缓存层调用。
     * </p>
     *
     * @param sql 要解析的SQL语句
     * @return 解析结果
     * @throws JSQLParserException 如果SQL解析失败
     */
    private static Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> doParseSql(String sql) throws JSQLParserException {
        if (StrUtil.isBlank(sql)) {
            log.warn("Attempted to parse empty SQL statement");
            return Pair.of(Collections.emptyMap(), Collections.emptyList());
        }

        try {
            // 1. 将SQL中的?占位符替换成自定义的特殊符号，以便SQL解析器识别
            String placeholderSql = question2Placeholder(sql);
            log.debug("Replaced placeholders in SQL: " + placeholderSql);

            // 2. 使用JSQLParser解析SQL语句
            Statement statement = CCJSqlParserUtil.parse(placeholderSql);

            // 3. 使用访问者模式提取表字段信息和加密字段信息
            PoJoEncrtptorStatementVisitor visitor = new PoJoEncrtptorStatementVisitor();
            statement.accept(visitor);

            // 4. 获取解析结果并建立占位符索引映射
            Map<String, ColumnTableDto> placeholderColumnTableMap = visitor.getPlaceholderColumnTableMap();
            for (Map.Entry<String, ColumnTableDto> entry : placeholderColumnTableMap.entrySet()) {
                String key = entry.getKey();
                ColumnTableDto value = entry.getValue();

                if (key.startsWith(PLACEHOLDER)) {
                    Integer index = Integer.parseInt(key.replace(PLACEHOLDER, ""));
                    value.setInsertFieldIndex(index);
                }
            }

            List<FieldEncryptorInfoDto> fieldEncryptorInfos = visitor.getFieldEncryptorInfos();
            log.debug("Parsed SQL: found " + placeholderColumnTableMap.size() + " placeholders, "
                    + fieldEncryptorInfos.size() + " fields need encryption");

            return Pair.of(placeholderColumnTableMap, fieldEncryptorInfos);
        } catch (JSQLParserException e) {
            log.error("Failed to parse SQL: " + sql + ", error: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 将SQL中的问号占位符（?）替换为自定义占位符
     *
     * <p>该方法会将SQL中的所有问号占位符替换为格式为{@code SECURT_KIT_PLACEHOLDER_N}的占位符，
     * 其中N为占位符的索引（从1开始）。这样做的目的是让SQL解析器能够识别和区分不同的占位符。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * 输入: "UPDATE user SET name = ?, phone = ? WHERE id = ?"
     * 输出: "UPDATE user SET name = SECURT_KIT_PLACEHOLDER_1, phone = SECURT_KIT_PLACEHOLDER_2 WHERE id = SECURT_KIT_PLACEHOLDER_3"
     * }</pre>
     *
     * @param sql 原始SQL语句
     * @return 替换占位符后的SQL语句，如果输入为空则返回原值
     */
    public static String question2Placeholder(String sql) {
        if (StrUtil.isBlank(sql)) {
            return sql;
        }

        // 获取当前线程的计数器，每个线程独立计数，避免并发干扰
        AtomicInteger counter = PLACEHOLDER_COUNTER.get();
        // 重置计数器，确保每次解析都从1开始
        counter.set(1);

        // 使用正则表达式匹配所有问号占位符
        Pattern pattern = Pattern.compile("\\?");
        Matcher matcher = pattern.matcher(sql);

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String replacement = PLACEHOLDER + counter.getAndIncrement();
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 判断给定的表集合是否需要加密处理
     *
     * <p>该方法会检查传入的表名集合是否与配置中需要加密的表有交集。
     * 如果有任何表在加密配置中，则返回true，表示需要进行加密处理。</p>
     *
     * @param tables 要检查的表名集合，不能为null
     * @return 如果需要加密处理返回true，否则返回false
     * @since 1.0.0
     */
    public static boolean needEncrypt(Collection<String> tables) {
        if (CollectionUtil.isEmpty(tables)) {
            return false;
        }
        Set<String> configuredTables = TableCache.getTables();
        return CollectionUtil.containsAny(configuredTables, tables);
    }
}
