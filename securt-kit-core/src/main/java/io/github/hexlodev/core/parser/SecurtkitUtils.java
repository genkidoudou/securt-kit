package io.github.hexlodev.core.parser;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import io.github.hexlodev.core.TableCache;
import io.github.hexlodev.core.config.ConfigInitializer;
import io.github.hexlodev.core.parser.dto.ColumnTableDto;
import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
import io.github.hexlodev.core.parser.visitor.PoJoEncrtptorStatementVisitor;
import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
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
     * 解析SQL语句，获取占位符与表字段的映射关系以及需要加密的字段列表（向后兼容）
     * 
     * @param sql 要解析的SQL语句
     * @return 解析结果
     * @throws JSQLParserException 如果SQL解析失败
     * @since 1.0.0
     */
    public static Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseSql(String sql) throws JSQLParserException {
        return parseSql(sql, null);
    }

    /**
     * 解析SQL语句，获取占位符与表字段的映射关系以及需要加密的字段列表（支持多数据源）
     * 
     * <p>该方法使用缓存机制，相同 SQL 的解析结果会被缓存，显著提升性能。
     * 缓存使用 LRU 算法，自动淘汰最久未使用的条目。</p>
     *
     * <p>该方法会执行以下步骤：</p>
     * <ol>
     *   <li>检查缓存，如果命中则直接返回</li>
     *   <li>将SQL中的问号占位符替换为自定义占位符</li>
     *   <li>使用JSQLParser解析SQL语句</li>
     *   <li>通过访问者模式提取表字段信息和加密字段信息（根据数据源标识）</li>
     *   <li>建立占位符索引与表字段的映射关系</li>
     *   <li>将解析结果存入缓存</li>
     * </ol>
     *
     * <p>性能优化：</p>
     * <ul>
     *   <li>使用缓存机制，避免重复解析相同 SQL</li>
     *   <li>SQL 规范化处理，提升缓存命中率</li>
     *   <li>使用 LRU 算法，自动管理缓存大小</li>
     * </ul>
     *
     * @param sql 要解析的SQL语句，包含问号占位符（如：UPDATE user SET name = ? WHERE id = ?），不能为 null 或空白
     * @param datasourceId 数据源标识，如果为 null 则使用默认数据源
     * @return Pair对象，包含：
     * <ul>
     *   <li>Key: 占位符到ColumnTableDto的映射（占位符名称 -> 表字段信息）</li>
     *   <li>Value: 需要加密的字段信息列表</li>
     * </ul>
     * @throws IllegalArgumentException 如果 SQL 为 null 或空白
     * @throws JSQLParserException 如果SQL解析失败
     * @since 1.1.0
     * @see SqlParseCache
     * @see ColumnTableDto
     * @see FieldEncryptorInfoDto
     */
    public static Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseSql(String sql, String datasourceId) throws JSQLParserException {
        if (StrUtil.isBlank(sql)) {
            throw new IllegalArgumentException("SQL statement cannot be null or blank");
        }
        // 使用缓存进行解析（注意：缓存不考虑数据源标识，因为SQL本身是相同的）
        // 但解析时需要根据数据源标识过滤加密字段
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = SqlParseCache.parseSql(sql, SecurtkitUtils::doParseSql);
        
        // 根据数据源标识过滤加密字段信息并设置 fieldEncryptor
        // 注意：即使 datasourceId 为 null，也要执行过滤，使用 "default" 作为默认值
        if (result != null && result.getValue() != null) {
            List<FieldEncryptorInfoDto> filteredFields = new ArrayList<>();
            String dsId = StrUtil.isBlank(datasourceId) ? "default" : datasourceId;
            
            if (log.isDebugEnabled()) {
                log.debug("Filtering {} fields for datasource-id: {}", result.getValue().size(), dsId);
            }
            
            for (FieldEncryptorInfoDto field : result.getValue()) {
                // 检查该字段在该数据源中是否需要加密
                Class<? extends FieldEncryptorStrategy> strategy = TableCache.getTableFieldEncryptStrategy(
                    field.getSourceTableName(), field.getSourceColumn(), dsId);
                if (strategy != null) {
                    FieldEncryptorInfoDto rebuilt = FieldEncryptorInfoDto.builder()
                            .columnName(field.getColumnName())
                            .sourceColumn(field.getSourceColumn())
                            .sourceTableName(field.getSourceTableName())
                            .fieldEncryptor(strategy)
                            .build();
                    filteredFields.add(rebuilt);
                    if (log.isDebugEnabled()) {
                        log.debug("  Field matched: columnName={}, sourceTable={}, sourceColumn={}, strategy={}",
                                field.getColumnName(), field.getSourceTableName(), field.getSourceColumn(), strategy.getName());
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("  Field skipped (no strategy): columnName={}, sourceTable={}, sourceColumn={}",
                                field.getColumnName(), field.getSourceTableName(), field.getSourceColumn());
                    }
                }
            }
            
            if (log.isDebugEnabled()) {
                log.debug("Filtered result: {} fields need encryption (datasource-id: {})", filteredFields.size(), dsId);
            }
            
            return Pair.of(result.getKey(), filteredFields);
        }
        
        return result;
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
            // 1. 规范化 SQL（去除多余空格、统一换行符）
            String normalizedSql = normalizeSqlForParsing(sql);
            if (log.isDebugEnabled()) {
                log.debug("Normalized SQL: {}", normalizedSql);
            }

            // 2. 将SQL中的?占位符替换成自定义的特殊符号，以便SQL解析器识别
            String placeholderSql = question2Placeholder(normalizedSql);
            if (log.isDebugEnabled()) {
                log.debug("Replaced placeholders in SQL: {}", placeholderSql);
            }

            // 3. 使用JSQLParser解析SQL语句
            Statement statement = CCJSqlParserUtil.parse(placeholderSql);

            // 4. 使用访问者模式提取表字段信息和加密字段信息
            // 注意：这里不传递 datasourceId，因为 doParseSql 方法没有 datasourceId 参数
            // datasourceId 的过滤会在后续步骤中进行
            PoJoEncrtptorStatementVisitor visitor = new PoJoEncrtptorStatementVisitor();
            statement.accept(visitor);

            // 5. 获取解析结果并建立占位符索引映射
            Map<String, ColumnTableDto> placeholderColumnTableMap = visitor.getPlaceholderColumnTableMap();
            for (Map.Entry<String, ColumnTableDto> entry : placeholderColumnTableMap.entrySet()) {
                String key = entry.getKey();
                ColumnTableDto value = entry.getValue();

                if (key.startsWith(PLACEHOLDER)) {
                    // 使用 substring 替代 replace，性能更好
                    Integer index = Integer.parseInt(key.substring(PLACEHOLDER.length()));
                    value.setInsertFieldIndex(index);
                }
            }

            List<FieldEncryptorInfoDto> fieldEncryptorInfos = visitor.getFieldEncryptorInfos();
            // 使用参数化日志，避免字符串拼接
            if (log.isDebugEnabled()) {
                log.debug("Parsed SQL: found {} placeholders, {} fields need encryption",
                        placeholderColumnTableMap.size(), fieldEncryptorInfos.size());
            }

            return Pair.of(placeholderColumnTableMap, fieldEncryptorInfos);
        } catch (JSQLParserException e) {
            log.error("Failed to parse SQL: {}, error: {}", sql, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 将SQL中的问号占位符（?）替换为自定义占位符
     *
     * <p>该方法会将SQL中的所有问号占位符替换为格式为{@code SECURT_KIT_PLACEHOLDER_N}的占位符，
     * 其中N为占位符的索引（从1开始）。这样做的目的是让SQL解析器能够识别和区分不同的占位符。</p>
     * 
     * <p>性能优化：</p>
     * <ul>
     *   <li>使用 ThreadLocal 计数器，每个线程独立计数，避免并发干扰</li>
     *   <li>预分配 StringBuffer 容量，减少扩容开销</li>
     *   <li>使用正则表达式高效匹配和替换</li>
     * </ul>
     *
     * <p>示例：</p>
     * <pre>{@code
     * 输入: "UPDATE user SET name = ?, phone = ? WHERE id = ?"
     * 输出: "UPDATE user SET name = SECURT_KIT_PLACEHOLDER_1, phone = SECURT_KIT_PLACEHOLDER_2 WHERE id = SECURT_KIT_PLACEHOLDER_3"
     * }</pre>
     *
     * @param sql 原始SQL语句，不能为 null（空白字符串会直接返回）
     * @return 替换占位符后的SQL语句，如果输入为空白则返回原值
     * @throws IllegalArgumentException 如果 SQL 为 null
     * @since 1.0.0
     */
    public static String question2Placeholder(String sql) {
        if (sql == null) {
            throw new IllegalArgumentException("SQL statement cannot be null");
        }
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

        // 使用 StringBuffer（Matcher.appendReplacement 要求使用 StringBuffer）
        // 预估容量：原SQL长度 + 占位符长度 * 预估占位符数量（10个），减少扩容开销
        StringBuffer sb = new StringBuffer(sql.length() + PLACEHOLDER.length() * 10);
        while (matcher.find()) {
            // 构建替换字符串（Java 编译器会优化简单的字符串拼接）
            String replacement = PLACEHOLDER + counter.getAndIncrement();
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 规范化 SQL 语句以便解析
     * <p>
     * 该方法会对 SQL 进行规范化处理，解决多行格式、多余空格等问题：
     * </p>
     * <ul>
     *   <li>统一换行符：将 \r\n 和 \r 统一为 \n</li>
     *   <li>去除行首行尾空格：清理每行前后的空白字符</li>
     *   <li>压缩连续空白：将多个连续空白字符（空格、制表符等）压缩为单个空格</li>
     *   <li>保留必要的空格：确保关键字、标识符之间有适当的空格分隔</li>
     * </ul>
     *
     * <p>示例：</p>
     * <pre>{@code
     * 输入: "INSERT INTO user (id,\n     name,\n     phone) VALUES (?, ?, ?)"
     * 输出: "INSERT INTO user (id, name, phone) VALUES (?, ?, ?)"
     * }</pre>
     *
     * @param sql 原始 SQL 语句，不能为 null
     * @return 规范化后的 SQL 语句
     * @throws IllegalArgumentException 如果 SQL 为 null
     * @since 1.0.0
     */
    private static String normalizeSqlForParsing(String sql) {
        if (sql == null) {
            throw new IllegalArgumentException("SQL statement cannot be null");
        }
        if (StrUtil.isBlank(sql)) {
            return sql;
        }

        // 1. 统一换行符：将 \r\n 和 \r 统一为 \n
        String normalized = sql.replace("\r\n", "\n").replace("\r", "\n");

        // 2. 按行处理，去除每行的首尾空格
        String[] lines = normalized.split("\n");
        StringBuilder sb = new StringBuilder(sql.length());
        boolean firstLine = true;

        for (String line : lines) {
            // 去除行首行尾空格
            String trimmedLine = line.trim();

            // 跳过空行
            if (trimmedLine.isEmpty()) {
                continue;
            }

            // 如果不是第一行，添加空格分隔（而不是换行符）
            if (!firstLine) {
                sb.append(' ');
            }
            firstLine = false;

            // 添加处理后的行内容
            sb.append(trimmedLine);
        }

        // 3. 压缩连续空白字符（空格、制表符等）为单个空格
        // 但保留字符串字面量中的空格（简单处理：避免在引号内替换）
        String result = sb.toString();
        // 使用正则表达式：将多个连续空白字符（空格、制表符、换行符等）替换为单个空格
        // 但需要小心处理字符串字面量
        result = result.replaceAll("\\s+", " ");

        // 4. 清理可能的特殊情况：确保关键字和标识符之间有适当的空格
        // 例如：确保括号前后有适当的空格（但保留函数调用的情况）
        // 这里做简单的处理，主要针对常见的 SQL 模式
        result = result.replaceAll("\\s*,\\s*", ", ");  // 统一逗号后的空格
        result = result.replaceAll("\\s*\\(\\s*", " (");  // 统一左括号前的空格
        result = result.replaceAll("\\s*\\)\\s*", ") ");  // 统一右括号后的空格
        result = result.replaceAll("\\s*=\\s*", " = ");   // 统一等号前后的空格

        // 5. 去除首尾空格
        result = result.trim();

        return result;
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
        return ConfigInitializer.extractPureTableName(tableName);
    }

    /**
     * 判断给定的表集合是否需要加密处理
     *
     * <p>该方法会检查传入的表名集合是否与配置中需要加密的表有交集。
     * 如果有任何表在加密配置中，则返回true，表示需要进行加密处理。</p>
     * 
     * <p>支持带数据库名的表名格式（如 testdb.user），会自动提取纯表名进行匹配。</p>
     *
     * @param tables 要检查的表名集合，可以为 null 或空集合
     * @return 如果需要加密处理返回true，否则返回false
     * @since 1.0.0
     */
    /**
     * 判断表集合中是否有需要加密的表（向后兼容）
     * 
     * @param tables 表名集合
     * @return 如果需要加密返回 true
     * @since 1.0.0
     */
    public static boolean needEncrypt(Collection<String> tables) {
        return needEncrypt(tables, null);
    }

    /**
     * 判断表集合中是否有需要加密的表（支持多数据源）
     * 
     * <p>检查给定的表名集合中是否包含配置了加密的表。
     * 用于快速判断是否需要执行加密/解密操作，避免不必要的SQL解析。</p>
     * 
     * <p>性能优化：</p>
     * <ul>
     *   <li>使用 Set.contains() 进行 O(1) 查找</li>
     *   <li>支持表名规范化（自动提取纯表名）</li>
     *   <li>空集合快速返回 false</li>
     * </ul>
     * 
     * <p>使用示例：</p>
     * <pre>{@code
     * Set<String> tables = Set.of("user", "orders");
     * if (SecurtkitUtils.needEncrypt(tables, "primary")) {
     *     // 执行加密/解密逻辑
     * }
     * }</pre>
     *
     * @param tables 表名集合，可以为 null 或空集合
     * @param datasourceId 数据源标识，如果为 null 则使用默认数据源
     * @return 如果表集合中包含需要加密的表则返回 true，否则返回 false
     * @since 1.1.0
     * @see TableCache#getTables()
     * @see TableCache#concatTable(String, String)
     */
    public static boolean needEncrypt(Collection<String> tables, String datasourceId) {
        if (CollectionUtil.isEmpty(tables)) {
            return false;
        }
        
        // 提取纯表名集合（去掉数据库名和schema前缀）
        Set<String> pureTableNames = new HashSet<>();
        for (String table : tables) {
            String pureTableName = extractPureTableName(table);
            if (StrUtil.isNotBlank(pureTableName)) {
                pureTableNames.add(pureTableName);
            }
        }
        
        // 根据数据源标识检查表是否需要加密
        for (String pureTableName : pureTableNames) {
            if (TableCache.concatTable(pureTableName, datasourceId)) {
                return true;
            }
        }
        
        return false;
    }
}
