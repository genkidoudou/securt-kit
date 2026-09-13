package io.github.genkidoudou.core.utils;

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
 * @see SqlToken
 * @see TableNameVisitor
 * @see StringPool
 * @since 2019-04-22
 */
public final class TableNameParser {

    private static final String TOKEN_SET = "set";
    private static final String TOKEN_OF = "of";
    private static final String TOKEN_DUAL = "dual";
    private static final String TOKEN_DELETE = "delete";
    private static final String TOKEN_CREATE = "create";
    private static final String TOKEN_INDEX = "index";

    private static final String KEYWORD_JOIN = "join";
    private static final String KEYWORD_INTO = "into";
    private static final String KEYWORD_TABLE = "table";
    private static final String KEYWORD_FROM = "from";
    private static final String KEYWORD_USING = "using";
    private static final String KEYWORD_UPDATE = "update";
    private static final String KEYWORD_DUPLICATE = "duplicate";

    private static final List<String> concerned = Arrays.asList(KEYWORD_TABLE, KEYWORD_INTO, KEYWORD_JOIN, KEYWORD_USING, KEYWORD_UPDATE);
    private static final List<String> ignored = Arrays.asList(StringPool.LEFT_BRACKET, TOKEN_SET, TOKEN_OF, TOKEN_DUAL, 
            "if", "not", "exists", "current", "timestamp", "default", "on", "update", "references", "foreign", "key");

    /**
     * 该表达式会匹配 SQL 中不是 SQL TOKEN 的部分，比如换行符，注释信息，结尾的 {@code ;} 等。
     * <p>
     * 排除的项目包括：
     * 1、以 -- 开头的注释信息
     * 2、;
     * 3、空白字符
     * 4、使用 /* * / 注释的信息
     * 5、把 ,() 也要分出来
     */
    private static final Pattern NON_SQL_TOKEN_PATTERN = Pattern.compile("(--[^\\v]+)|;|(\\s+)|((?s)/[*].*?[*]/)"
            + "|(((\\b|\\B)(?=[,()]))|((?<=[,()])(\\b|\\B)))"
    );

    private final List<SqlToken> tokens;

    /**
     * 从 SQL 中提取表名称
     *
     * @param sql 需要解析的 SQL 语句
     */
    public TableNameParser(String sql) {
        tokens = fetchAllTokens(sql);
    }

    /**
     * 接受一个新的访问者，并访问当前 SQL 的表名称
     * <p>
     * 现在我们改成了访问者模式，不在对以前的 SQL 做改动
     * 同时，你可以方便的获得表名位置的索引
     *
     * @param visitor 访问者
     */
    public void accept(TableNameVisitor visitor) {
        int index = 0;
        String first = tokens.get(index).getValue();
        if (isOracleSpecialDelete(first, tokens, index)) {
            visitNameToken(tokens.get(index + 1), visitor);
        } else if (isCreateIndex(first, tokens, index)) {
            // 处理 CREATE INDEX 语句，跳过 IF NOT EXISTS
            index = processCreateIndex(tokens, index, visitor);
        } else if (isCreateTable(first, tokens, index)) {
            // 处理 CREATE TABLE 语句
            index = processCreateTable(tokens, index, visitor);
        } else {
            while (hasMoreTokens(tokens, index)) {
                String current = tokens.get(index++).getValue();
                if (isFromToken(current)) {
                    processFromToken(tokens, index, visitor);
                } else if (isOnDuplicateKeyUpdate(current, index)) {
                    index = skipDuplicateKeyUpdateIndex(index);
                } else if (concerned.contains(current.toLowerCase())) {
                    if (hasMoreTokens(tokens, index)) {
                        SqlToken next = tokens.get(index++);
                        visitNameToken(next, visitor);
                    }
                }
            }
        }
    }

    /**
     * 表名访问器
     */
    public interface TableNameVisitor {
        /**
         * @param name 表示表名称的 token
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
     * 判断是否是 CREATE TABLE 语句
     */
    private boolean isCreateTable(String current, List<SqlToken> tokens, int index) {
        if (TOKEN_CREATE.equalsIgnoreCase(current) && hasMoreTokens(tokens, index + 1)) {
            String next = tokens.get(index + 1).getValue();
            return KEYWORD_TABLE.equalsIgnoreCase(next);
        }
        return false;
    }

    /**
     * 处理 CREATE TABLE 语句，跳过 IF NOT EXISTS 等关键字
     */
    private int processCreateTable(List<SqlToken> tokens, int index, TableNameVisitor visitor) {
        // CREATE TABLE 已经处理，index 指向 CREATE
        index += 2; // 跳过 CREATE TABLE
        
        // 跳过 IF NOT EXISTS
        index = skipIfNotExists(tokens, index);
        
        // 现在 index 应该指向表名
        if (hasMoreTokens(tokens, index)) {
            visitNameToken(tokens.get(index), visitor);
        }
        
        return index + 1;
    }

    /**
     * 处理 CREATE INDEX 语句，跳过 IF NOT EXISTS
     */
    private int processCreateIndex(List<SqlToken> tokens, int index, TableNameVisitor visitor) {
        // CREATE INDEX 已经处理，index 指向 CREATE
        index += 2; // 跳过 CREATE INDEX
        
        // 跳过 IF NOT EXISTS
        index = skipIfNotExists(tokens, index);
        
        // 跳过索引名
        if (hasMoreTokens(tokens, index)) {
            index++; // 跳过索引名
        }
        
        // 跳过 ON
        if (hasMoreTokens(tokens, index) && "on".equalsIgnoreCase(tokens.get(index).getValue())) {
            index++; // 跳过 ON
        }
        
        // 现在 index 应该指向表名
        if (hasMoreTokens(tokens, index)) {
            visitNameToken(tokens.get(index), visitor);
        }
        
        return index + 1;
    }

    /**
     * 跳过 IF NOT EXISTS 关键字
     */
    private int skipIfNotExists(List<SqlToken> tokens, int index) {
        if (hasMoreTokens(tokens, index)) {
            String token = tokens.get(index).getValue();
            if ("if".equalsIgnoreCase(token)) {
                index++; // 跳过 IF
                if (hasMoreTokens(tokens, index) && "not".equalsIgnoreCase(tokens.get(index).getValue())) {
                    index++; // 跳过 NOT
                    if (hasMoreTokens(tokens, index) && "exists".equalsIgnoreCase(tokens.get(index).getValue())) {
                        index++; // 跳过 EXISTS
                    }
                }
            }
        }
        return index;
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
     * parser tables
     *
     * @return table names extracted out of sql
     * @see #accept(TableNameVisitor)
     */
    public Set<String> tables() {
        Map<String, String> tableMap = new HashMap<>();
        List<SqlToken> allTokens = new ArrayList<>();
        accept(token -> {
            allTokens.add(token);
        });

        // 按位置排序，确保顺序正确
        allTokens.sort(Comparator.comparingInt(SqlToken::getStart));

        // 处理表名，合并点号后有空格的情况（如 db. sys_user -> db.sys_user）
        // 需要检查原始 token 列表，因为 sys_user 可能没有被访问
        for (int i = 0; i < allTokens.size(); i++) {
            SqlToken token = allTokens.get(i);
            String name = token.getValue();

            // 如果当前 token 以点号结尾，需要从原始 token 列表中找到下一个 token
            if (name.endsWith(".")) {
                // 在原始 token 列表中找到当前 token 的位置
                int tokenIndex = -1;
                for (int j = 0; j < tokens.size(); j++) {
                    if (tokens.get(j).getStart() == token.getStart()) {
                        tokenIndex = j;
                        break;
                    }
                }

                // 如果在原始 token 列表中找到，且下一个 token 存在，则尝试合并
                if (tokenIndex >= 0 && tokenIndex + 1 < tokens.size()) {
                    SqlToken nextToken = tokens.get(tokenIndex + 1);
                    String nextValue = nextToken.getValue();

                    // 如果下一个 token 不是关键字或分隔符，则合并
                    if (!isKeywordOrSeparator(nextValue)) {
                        name = name + nextValue; // 合并为 database.table
                        // 检查下一个 token 是否也在 allTokens 中，如果在则跳过
                        boolean nextTokenInAllTokens = false;
                        for (SqlToken t : allTokens) {
                            if (t.getStart() == nextToken.getStart()) {
                                nextTokenInAllTokens = true;
                                break;
                            }
                        }
                        if (nextTokenInAllTokens) {
                            i++; // 跳过下一个 token
                        }
                    }
                }
            }

            // 使用小写作为key去重，但保留原始大小写作为value
            tableMap.putIfAbsent(name.toLowerCase(), name);
        }

        return new HashSet<>(tableMap.values());
    }

    /**
     * 检查字符串是否是关键字或分隔符
     */
    private static boolean isKeywordOrSeparator(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        String lowerValue = value.toLowerCase();
        // 检查是否是 SQL 关键字
        return lowerValue.equals(KEYWORD_JOIN) ||
                lowerValue.equals(KEYWORD_INTO) ||
                lowerValue.equals(KEYWORD_FROM) ||
                lowerValue.equals(KEYWORD_UPDATE) ||
                lowerValue.equals(KEYWORD_USING) ||
                lowerValue.equals("on") ||
                lowerValue.equals("where") ||
                lowerValue.equals("and") ||
                lowerValue.equals("or") ||
                lowerValue.equals("group") ||
                lowerValue.equals("order") ||
                lowerValue.equals("having") ||
                lowerValue.equals("limit") ||
                lowerValue.equals("union") ||
                lowerValue.equals(StringPool.COMMA) ||
                lowerValue.equals(StringPool.LEFT_BRACKET) ||
                lowerValue.equals(StringPool.RIGHT_BRACKET);
    }

    /**
     * SQL 词
     */
    public static class SqlToken implements Comparable<SqlToken> {
        private final int start;
        private final int end;
        private final String value;

        private SqlToken(int start, int end, String value) {
            this.start = start;
            this.end = end;
            this.value = value;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public String getValue() {
            return value;
        }

        @Override
        public int compareTo(SqlToken o) {
            return Integer.compare(start, o.start);
        }

        @Override
        public String toString() {
            return value;
        }

    }



    public static void main(String[] args) {
        String sql = "select u.user_id,\n" +
                "               u.dept_id,\n" +
                "               u.user_name,\n" +
                "               u.nick_name,\n" +
                "               u.email,\n" +
                "               u.avatar_id,\n" +
                "               u.phonenumber,\n" +
                "\n" +
                "               u.sex,\n" +
                "               u.status,\n" +
                "               u.del_flag,\n" +
                "               u.login_ip,\n" +
                "               u.login_date,\n" +
                "               u.create_by,\n" +
                "               u.create_time,\n" +
                "               u.remark,\n" +
                "               u.user_type,\n" +
                "               u.source,\n" +
                "               u.real_name,\n" +
                "               d.dept_id,\n" +
                "               d.parent_id,\n" +
                "               d.ancestors,\n" +
                "               d.dept_name,\n" +
                "               d.order_num,\n" +
                "               d.leader,\n" +
                "               d.status as dept_status,\n" +
                "               d.area_code,\n" +
                "               r.role_id,\n" +
                "               r.role_name,\n" +
                "               r.role_key,\n" +
                "               r.role_sort,\n" +
                "               r.data_scope,\n" +
                "               r.status as role_status\n" +
                "        FROM  business_platform_sy. sys_user u\n" +
                "                 left join  business_platform_sy. sys_dept d on u.dept_id = d.dept_id\n" +
                "                 left join  business_platform_sy. sys_user_role ur on u.user_id = ur.user_id\n" +
                "                 left join  business_platform_sy. sys_role r on r.role_id = ur.role_id\n" +
                "     \n" +
                "        where u.user_id =1985684407922077696";

        String sql2 = "select * from db. sys_user";
        TableNameParser parser = new TableNameParser(sql);
        System.out.println(parser.tables());
    }

}