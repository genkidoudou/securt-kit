package io.github.hexlodev.core.utils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 表名解析器
 *
 * <p>这是一个超轻量级、超快速的SQL表名解析器，能够从各种SQL语句中提取表名。
 * 支持Oracle方言SQL以及标准的SQL语句。</p>
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>解析SELECT语句中的FROM子句表名</li>
 *   <li>解析INSERT语句中的INTO子句表名</li>
 *   <li>解析UPDATE语句中的表名</li>
 *   <li>解析DELETE语句中的表名</li>
 *   <li>解析JOIN语句中的表名</li>
 *   <li>解析CREATE TABLE语句中的表名</li>
 *   <li>支持多表查询和子查询</li>
 *   <li>支持Oracle特殊DELETE语法</li>
 *   <li>支持MySQL的ON DUPLICATE KEY UPDATE语法</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 基本用法
 * TableNameParser parser = new TableNameParser("SELECT * FROM users WHERE id = 1");
 * Collection<String> tables = parser.tables();
 * System.out.println(tables); // 输出: [users]
 *
 * // 多表查询
 * parser = new TableNameParser("SELECT u.name, o.amount FROM users u JOIN orders o ON u.id = o.user_id");
 * tables = parser.tables();
 * System.out.println(tables); // 输出: [users, orders]
 *
 * // 使用访问者模式
 * parser.accept(token -> {
 *     System.out.println("表名: " + token.getValue() + ", 位置: " + token.getStart());
 * });
 * }</pre>
 *
 * <p>支持的SQL类型：</p>
 * <ul>
 *   <li>SELECT ... FROM table1, table2</li>
 *   <li>INSERT INTO table VALUES (...)</li>
 *   <li>UPDATE table SET ...</li>
 *   <li>DELETE FROM table WHERE ...</li>
 *   <li>DELETE table WHERE ... (Oracle语法)</li>
 *   <li>CREATE TABLE table (...)</li>
 *   <li>SELECT ... FROM table1 JOIN table2 ON ...</li>
 *   <li>子查询和复杂嵌套查询</li>
 * </ul>
 *
 * <p>技术特点：</p>
 * <ul>
 *   <li>使用正则表达式进行SQL词法分析</li>
 *   <li>采用访问者模式支持扩展功能</li>
 *   <li>支持表名位置索引获取</li>
 *   <li>自动去重和规范化表名</li>
 *   <li>异常安全，解析失败不影响主流程</li>
 * </ul>
 *
 * <p>原始项目：https://github.com/mnadeem/sql-table-name-parser</p>
 *
 * @author Nadeem Mohammad, hcl
 * @author hexlodev (修改和增强)
 * @since 2019-04-22
 * @see SqlToken
 * @see TableNameVisitor
 * @see StringPool
 */
public final class TableNameParser {

    // ==================== SQL关键字常量 ====================

    /** SET关键字 */
    private static final String TOKEN_SET = "set";

    /** OF关键字 */
    private static final String TOKEN_OF = "of";

    /** DUAL关键字（Oracle虚拟表） */
    private static final String TOKEN_DUAL = "dual";

    /** DELETE关键字 */
    private static final String TOKEN_DELETE = "delete";

    /** CREATE关键字 */
    private static final String TOKEN_CREATE = "create";

    /** INDEX关键字 */
    private static final String TOKEN_INDEX = "index";

    // ==================== SQL操作关键字 ====================

    /** JOIN关键字 */
    private static final String KEYWORD_JOIN = "join";

    /** INTO关键字 */
    private static final String KEYWORD_INTO = "into";

    /** TABLE关键字 */
    private static final String KEYWORD_TABLE = "table";

    /** FROM关键字 */
    private static final String KEYWORD_FROM = "from";

    /** USING关键字 */
    private static final String KEYWORD_USING = "using";

    /** UPDATE关键字 */
    private static final String KEYWORD_UPDATE = "update";

    /** DUPLICATE关键字（MySQL特有） */
    private static final String KEYWORD_DUPLICATE = "duplicate";

    // ==================== 解析配置 ====================

    /** 需要关注的关键字列表，这些关键字后面通常跟着表名 */
    private static final List<String> concerned = Arrays.asList(KEYWORD_TABLE, KEYWORD_INTO, KEYWORD_JOIN, KEYWORD_USING, KEYWORD_UPDATE);

    /** 需要忽略的token列表，这些token不应该被识别为表名 */
    private static final List<String> ignored = Arrays.asList(StringPool.LEFT_BRACKET, TOKEN_SET, TOKEN_OF, TOKEN_DUAL);

    /**
     *  SQL词法分析正则表达式
     * @author luyanan
     * @since 2025/10/27
     */
    private static final Pattern NON_SQL_TOKEN_PATTERN = Pattern.compile("(--[^\\v]+)|;|(\\s+)|((?s)/[*].*?[*]/)"
            + "|(((\\b|\\B)(?=[,()]))|((?<=[,()])(\\b|\\B)))"
    );

    /** SQL token列表 */
    private final List<SqlToken> tokens;

    /**
     * 构造函数
     *
     * <p>创建一个新的表名解析器，解析指定的SQL语句。</p>
     *
     * @param sql 需要解析的SQL语句，不能为null
     * @throws IllegalArgumentException 如果sql为null
     */
    public TableNameParser(String sql) {
        if (sql == null) {
            throw new IllegalArgumentException("SQL cannot be null");
        }
        tokens = fetchAllTokens(sql);
    }

    /**
     * 接受表名访问者 - 访问者模式
     *
     * <p>使用访问者模式遍历SQL中的所有表名，为每个表名调用访问者的 {@code visit} 方法。
     * 这种方法允许调用者获取表名的详细信息，包括位置索引。</p>
     *
     * <p>解析过程：</p>
     * <ol>
     *   <li>检查是否为Oracle特殊DELETE语法</li>
     *   <li>检查是否为CREATE INDEX语句</li>
     *   <li>遍历所有token，查找关注的关键字</li>
     *   <li>处理FROM子句（包括多表JOIN）</li>
     *   <li>处理其他关键字后的表名</li>
     *   <li>跳过MySQL的ON DUPLICATE KEY UPDATE子句</li>
     * </ol>
     *
     * @param visitor 表名访问者，不能为null
     * @throws IllegalArgumentException 如果visitor为null
     */
    public void accept(TableNameVisitor visitor) {
        if (visitor == null) {
            throw new IllegalArgumentException("Visitor cannot be null");
        }

        int index = 0;
        String first = tokens.get(index).getValue();

        // 处理Oracle特殊DELETE语法：DELETE table WHERE ...
        if (isOracleSpecialDelete(first, tokens, index)) {
            visitNameToken(tokens.get(index + 1), visitor);
        }
        // 处理CREATE INDEX语句
        else if (isCreateIndex(first, tokens, index)) {
            visitNameToken(tokens.get(index + 4), visitor);
        }
        // 处理标准SQL语句
        else {
            while (hasMoreTokens(tokens, index)) {
                String current = tokens.get(index++).getValue();

                if (isFromToken(current)) {
                    // 处理FROM子句
                    processFromToken(tokens, index, visitor);
                } else if (isOnDuplicateKeyUpdate(current, index)) {
                    // 跳过MySQL的ON DUPLICATE KEY UPDATE
                    index = skipDuplicateKeyUpdateIndex(index);
                } else if (concerned.contains(current.toLowerCase())) {
                    // 处理其他关注的关键字
                    if (hasMoreTokens(tokens, index)) {
                        SqlToken next = tokens.get(index++);
                        visitNameToken(next, visitor);
                    }
                }
            }
        }
    }

    /**
     * 表名访问者接口
     *
     * <p>定义了访问表名的方法，使用访问者模式支持扩展功能。</p>
     */
    public interface TableNameVisitor {
        /**
         * 访问表名token
         *
         * @param name 表示表名称的token，包含表名值和位置信息
         */
        void visit(SqlToken name);
    }

    /**
     * 从 SQL 语句中提取出 所有的 SQL Token
     *
     * @param sql SQL
     * @return 语句
     */
    protected List<SqlToken> fetchAllTokens(String sql) {
        List<SqlToken> tokens = new ArrayList<>();
        Matcher matcher = NON_SQL_TOKEN_PATTERN.matcher(sql);
        int last = 0;
        while (matcher.find()) {
            int start = matcher.start();
            if (start != last) {
                tokens.add(new SqlToken(last, start, sql.substring(last, start)));
            }
            last = matcher.end();
        }
        if (last != sql.length()) {
            tokens.add(new SqlToken(last, sql.length(), sql.substring(last)));
        }
        return tokens;
    }

    /**
     * 如果是 DELETE 后面紧跟的不是 FROM 或者 * ,则 返回 true
     *
     * @param current 当前的 token
     * @param tokens  token 列表
     * @param index   索引
     * @return 判断是不是 Oracle 特殊的删除手法
     */
    private static boolean isOracleSpecialDelete(String current, List<SqlToken> tokens, int index) {
        if (TOKEN_DELETE.equalsIgnoreCase(current)) {
            if (hasMoreTokens(tokens, index++)) {
                String next = tokens.get(index).getValue();
                return !KEYWORD_FROM.equalsIgnoreCase(next) && !StringPool.ASTERISK.equals(next);
            }
        }
        return false;
    }

    private boolean isCreateIndex(String current, List<SqlToken> tokens, int index) {
        index++; // Point to next token
        if (TOKEN_CREATE.equalsIgnoreCase(current) && hasIthToken(tokens, index)) {
            String next = tokens.get(index).getValue();
            return TOKEN_INDEX.equalsIgnoreCase(next);
        }
        return false;
    }

    /**
     * @param current 当前token
     * @param index   索引
     * @return 判断是否是mysql的特殊语法 on duplicate key update
     */
    private boolean isOnDuplicateKeyUpdate(String current, int index) {
        if (KEYWORD_DUPLICATE.equalsIgnoreCase(current)) {
            if (hasMoreTokens(tokens, index++)) {
                String next = tokens.get(index).getValue();
                return KEYWORD_UPDATE.equalsIgnoreCase(next);
            }
        }
        return false;
    }

    private static boolean hasIthToken(List<SqlToken> tokens, int currentIndex) {
        return hasMoreTokens(tokens, currentIndex) && tokens.size() > currentIndex + 3;
    }

    private static boolean isFromToken(String currentToken) {
        return KEYWORD_FROM.equalsIgnoreCase(currentToken);
    }

    private int skipDuplicateKeyUpdateIndex(int index) {
        // on duplicate key update为mysql的固定写法，直接跳过即可。
        return index + 2;
    }

    private static void processFromToken(List<SqlToken> tokens, int index, TableNameVisitor visitor) {
        SqlToken sqlToken = tokens.get(index++);
        visitNameToken(sqlToken, visitor);

        String next = null;
        if (hasMoreTokens(tokens, index)) {
            next = tokens.get(index++).getValue();
        }

        if (shouldProcessMultipleTables(next)) {
            processNonAliasedMultiTables(tokens, index, next, visitor);
        } else {
            processAliasedMultiTables(tokens, index, sqlToken, visitor);
        }
    }

    private static void processNonAliasedMultiTables(List<SqlToken> tokens, int index, String nextToken, TableNameVisitor visitor) {
        while (nextToken.equals(StringPool.COMMA)) {
            visitNameToken(tokens.get(index++), visitor);
            if (hasMoreTokens(tokens, index)) {
                nextToken = tokens.get(index++).getValue();
            } else {
                break;
            }
        }
    }

    private static void processAliasedMultiTables(List<SqlToken> tokens, int index, SqlToken current, TableNameVisitor visitor) {
        String nextNextToken = null;
        if (hasMoreTokens(tokens, index)) {
            nextNextToken = tokens.get(index++).getValue();
        }

        if (shouldProcessMultipleTables(nextNextToken)) {
            while (hasMoreTokens(tokens, index) && nextNextToken.equals(StringPool.COMMA)) {
                if (hasMoreTokens(tokens, index)) {
                    current = tokens.get(index++);
                }
                if (hasMoreTokens(tokens, index)) {
                    index++;
                }
                if (hasMoreTokens(tokens, index)) {
                    nextNextToken = tokens.get(index++).getValue();
                }
                visitNameToken(current, visitor);
            }
        }
    }

    private static boolean shouldProcessMultipleTables(final String nextToken) {
        return nextToken != null && nextToken.equals(StringPool.COMMA);
    }

    private static boolean hasMoreTokens(List<SqlToken> tokens, int index) {
        return index < tokens.size();
    }

    private static void visitNameToken(SqlToken token, TableNameVisitor visitor) {
        String value = token.getValue().toLowerCase();
        if (!ignored.contains(value)) {
            visitor.visit(token);
        }
    }

    /**
     * 提取SQL中的所有表名
     *
     * <p>这是最常用的方法，返回SQL语句中所有表名的集合。
     * 表名会自动去重和规范化（保留原始大小写）。</p>
     *
     * <p>使用示例：</p>
     * <pre>{@code
     * TableNameParser parser = new TableNameParser("SELECT * FROM users u JOIN orders o ON u.id = o.user_id");
     * Collection<String> tables = parser.tables();
     * System.out.println(tables); // 输出: [users, orders]
     * }</pre>
     *
     * @return 表名集合，如果没有找到表名则返回空集合
     * @see #accept(TableNameVisitor)
     */
    public Collection<String> tables() {
        Map<String, String> tableMap = new HashMap<>();
        accept(token -> {
            String name = token.getValue();
            // 使用小写作为key去重，但保留原始大小写作为value
            tableMap.putIfAbsent(name.toLowerCase(), name);
        });
        return new HashSet<>(tableMap.values());
    }

    /**
     * SQL词法单元
     *
     * <p>表示SQL语句中的一个词法单元（token），包含token的值和在原SQL中的位置信息。
     * 这个类主要用于表名解析过程中，提供表名的位置索引以便进行更精确的处理。</p>
     *
     * <p>主要功能：</p>
     * <ul>
     *   <li>存储token的文本值</li>
     *   <li>记录token在SQL中的起始和结束位置</li>
     *   <li>支持按位置排序（实现Comparable接口）</li>
     *   <li>提供toString方法便于调试</li>
     * </ul>
     *
     * <p>使用示例：</p>
     * <pre>{@code
     * SqlToken token = new SqlToken(10, 15, "users");
     * System.out.println("表名: " + token.getValue()); // 输出: 表名: users
     * System.out.println("位置: " + token.getStart() + "-" + token.getEnd()); // 输出: 位置: 10-15
     * }</pre>
     *
     * @author hexlodev
     * @since 1.0.0
     */
    public static class SqlToken implements Comparable<SqlToken> {

        /** token在SQL中的起始位置 */
        private final int start;

        /** token在SQL中的结束位置 */
        private final int end;

        /** token的文本值 */
        private final String value;

        /**
         * 构造函数
         *
         * @param start token在SQL中的起始位置
         * @param end token在SQL中的结束位置
         * @param value token的文本值
         */
        private SqlToken(int start, int end, String value) {
            this.start = start;
            this.end = end;
            this.value = value;
        }

        /**
         * 获取token在SQL中的起始位置
         *
         * @return 起始位置索引
         */
        public int getStart() {
            return start;
        }

        /**
         * 获取token在SQL中的结束位置
         *
         * @return 结束位置索引
         */
        public int getEnd() {
            return end;
        }

        /**
         * 获取token的文本值
         *
         * @return token文本
         */
        public String getValue() {
            return value;
        }

        /**
         * 比较两个token的位置
         *
         * <p>用于按位置排序token，主要用于调试和日志记录。</p>
         *
         * @param o 要比较的另一个token
         * @return 位置比较结果
         */
        @Override
        public int compareTo(SqlToken o) {
            return Integer.compare(start, o.start);
        }

        /**
         * 返回token的字符串表示
         *
         * <p>主要用于调试和日志记录，返回token的文本值。</p>
         *
         * @return token的文本值
         */
        @Override
        public String toString() {
            return value;
        }
    }

}