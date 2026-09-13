package io.github.genkidoudou.monitor.util;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.StatementVisitorAdapter;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectVisitorAdapter;
import net.sf.jsqlparser.statement.update.Update;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 为监控台拼出的 SQL 补齐标识符引号，避免 H2/部分库把 {@code user}/{@code order} 等当保留字。
 */
public final class SqlIdentifierQuotes {

    private static final Pattern LEADING_BLOCK_COMMENTS =
            Pattern.compile("^\\s*((?:/\\*.*?\\*/\\s*)+)", Pattern.DOTALL);

    private SqlIdentifierQuotes() {
    }

    /**
     * 读取当前连接的标识符引号字符（MySQL 多为 {@code `}，H2/PostgreSQL 多为 {@code "}）。
     */
    public static String resolveQuote(Connection connection) throws SQLException {
        if (connection == null) {
            return "\"";
        }
        DatabaseMetaData meta = connection.getMetaData();
        String quote = meta != null ? meta.getIdentifierQuoteString() : null;
        if (quote == null || quote.trim().isEmpty() || " ".equals(quote)) {
            return "\"";
        }
        return quote;
    }

    /**
     * 用引号包裹裸标识符（已带引号则原样返回）。
     */
    public static String quoteIdentifier(String quote, String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return identifier;
        }
        String q = (quote == null || quote.isEmpty()) ? "\"" : quote;
        String trimmed = identifier.trim();
        if (isQuoted(trimmed, q)) {
            return trimmed;
        }
        String bare = stripQuotes(trimmed, q);
        return q + bare.replace(q, q + q) + q;
    }

    /**
     * 按 JDBC 元数据解析列的物理名（保留库内大小写），再加引号。
     * <p>
     * H2 等库中：表用 {@code "user"} 创建、列未加引号时，列实际为 {@code ID}；
     * 若仍拼 {@code ORDER BY "id"} 会报 Column "id" not found。
     * </p>
     */
    public static String quoteResolvedColumn(Connection connection, String table, String column)
            throws SQLException {
        String physicalTable = resolvePhysicalTable(connection, table);
        String physical = resolvePhysicalColumn(connection, physicalTable, column);
        return quoteIdentifier(resolveQuote(connection), physical);
    }

    /**
     * 按 JDBC 元数据解析表的物理名后再加引号。
     * <p>
     * H2 中未加引号创建的 {@code digest_user} 实际为 {@code DIGEST_USER}；
     * 拼 {@code FROM "digest_user"} 会报 Table "digest_user" not found。
     * </p>
     */
    public static String quoteResolvedTable(Connection connection, String table) throws SQLException {
        String physical = resolvePhysicalTable(connection, table);
        return quoteIdentifier(resolveQuote(connection), physical);
    }

    /**
     * 忽略大小写匹配表名，返回数据库中的物理表名；找不到则返回去引号后的逻辑名。
     */
    public static String resolvePhysicalTable(Connection connection, String table) throws SQLException {
        if (table == null || table.trim().isEmpty()) {
            return table;
        }
        String bareTable = stripQuotes(table.trim(), "\"");
        bareTable = stripQuotes(bareTable, "`");
        if (connection == null) {
            return bareTable;
        }
        DatabaseMetaData meta = connection.getMetaData();
        if (meta == null) {
            return bareTable;
        }
        String catalog = null;
        try {
            catalog = connection.getCatalog();
        } catch (SQLException ignore) {
            // ignore
        }
        // TABLE_NAME 精确/大小写变体；再扫一遍 ignore-case
        String[] candidates = {
                bareTable,
                bareTable.toUpperCase(java.util.Locale.ROOT),
                bareTable.toLowerCase(java.util.Locale.ROOT)
        };
        for (String candidate : candidates) {
            try (java.sql.ResultSet rs = meta.getTables(catalog, null, candidate, new String[]{"TABLE", "VIEW"})) {
                if (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (name != null && !name.isEmpty()) {
                        return name;
                    }
                }
            }
        }
        try (java.sql.ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (name != null && name.equalsIgnoreCase(bareTable)) {
                    return name;
                }
            }
        }
        return bareTable;
    }

    /**
     * 忽略大小写匹配表内列名，返回数据库中的物理列名；找不到则返回去引号后的逻辑名。
     */
    public static String resolvePhysicalColumn(Connection connection, String table, String column)
            throws SQLException {
        if (column == null || column.trim().isEmpty()) {
            return column;
        }
        String bareColumn = stripQuotes(column.trim(), "\"");
        bareColumn = stripQuotes(bareColumn, "`");
        if (connection == null || table == null || table.trim().isEmpty()) {
            return bareColumn;
        }
        String bareTable = resolvePhysicalTable(connection, table);
        DatabaseMetaData meta = connection.getMetaData();
        if (meta == null) {
            return bareColumn;
        }
        String catalog = null;
        try {
            catalog = connection.getCatalog();
        } catch (SQLException ignore) {
            // ignore
        }
        String[] tableCandidates = {
                bareTable,
                bareTable.toUpperCase(java.util.Locale.ROOT),
                bareTable.toLowerCase(java.util.Locale.ROOT)
        };
        for (String tableName : tableCandidates) {
            try (java.sql.ResultSet rs = meta.getColumns(catalog, null, tableName, null)) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    if (name != null && name.equalsIgnoreCase(bareColumn)) {
                        return name;
                    }
                }
            }
        }
        return bareColumn;
    }

    /**
     * 解析 SQL，给 FROM/JOIN/UPDATE/INSERT/DELETE 中的表名补引号后重新输出。
     * <p>
     * 解析失败时降级：仅对常见保留表名做字符串级替换。
     * </p>
     */
    public static String quoteTableIdentifiers(String sql, String quote) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }
        String q = (quote == null || quote.isEmpty()) ? "\"" : quote;
        String leadingComments = leadingBlockComments(sql);
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            statement.accept(new QuotingStatementVisitor(q, null));
            return leadingComments + statement;
        } catch (JSQLParserException e) {
            return quoteReservedTablesFallback(sql, q);
        }
    }

    /**
     * 结合当前连接元数据改写 SQL 中的表名引号（使用物理表名大小写）。
     */
    public static String quoteTableIdentifiers(Connection connection, String sql) throws SQLException {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }
        String q = resolveQuote(connection);
        String leadingComments = leadingBlockComments(sql);
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            statement.accept(new QuotingStatementVisitor(q, connection));
            return leadingComments + statement;
        } catch (JSQLParserException e) {
            return quoteReservedTablesFallback(sql, q);
        }
    }

    private static String leadingBlockComments(String sql) {
        Matcher matcher = LEADING_BLOCK_COMMENTS.matcher(sql);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static void quoteTable(Table table, String quote, Connection connection) {
        if (table == null) {
            return;
        }
        if (table.getName() != null && !table.getName().isEmpty()) {
            String name = table.getName();
            if (connection != null) {
                try {
                    name = resolvePhysicalTable(connection, name);
                } catch (SQLException ignore) {
                    // keep logical name
                }
            }
            table.setName(quoteIdentifier(quote, name));
        }
        if (table.getSchemaName() != null && !table.getSchemaName().isEmpty()) {
            table.setSchemaName(quoteIdentifier(quote, table.getSchemaName()));
        }
    }

    private static void quoteFromItem(FromItem fromItem, String quote, Connection connection) {
        if (fromItem instanceof Table) {
            quoteTable((Table) fromItem, quote, connection);
        }
    }

    private static boolean isQuoted(String identifier, String quote) {
        return identifier.length() >= 2
                && identifier.startsWith(quote)
                && identifier.endsWith(quote);
    }

    private static String stripQuotes(String identifier, String quote) {
        String result = identifier.trim();
        while (isQuoted(result, quote)) {
            result = result.substring(quote.length(), result.length() - quote.length());
        }
        // 兼容用户混用双引号
        while (result.length() >= 2 && result.startsWith("\"") && result.endsWith("\"")) {
            result = result.substring(1, result.length() - 1);
        }
        while (result.length() >= 2 && result.startsWith("`") && result.endsWith("`")) {
            result = result.substring(1, result.length() - 1);
        }
        return result;
    }

    /**
     * 解析失败时的兜底：给 FROM/JOIN/UPDATE/INTO 后紧跟的常见保留字加引号。
     */
    private static String quoteReservedTablesFallback(String sql, String quote) {
        String[] reserved = {"user", "order", "group", "select", "table", "key", "value", "check", "index", "view"};
        String result = sql;
        for (String word : reserved) {
            // FROM user / JOIN user / UPDATE user / INTO user —— 尚未被引号包裹
            String regex = "(?i)\\b(FROM|JOIN|UPDATE|INTO)\\s+(?!" + java.util.regex.Pattern.quote(quote)
                    + ")(" + word + ")\\b";
            result = result.replaceAll(regex, "$1 " + quote + "$2" + quote);
        }
        return result;
    }

    private static final class QuotingStatementVisitor extends StatementVisitorAdapter {
        private final String quote;
        private final Connection connection;

        private QuotingStatementVisitor(String quote, Connection connection) {
            this.quote = quote;
            this.connection = connection;
        }

        @Override
        public void visit(Select select) {
            select.getSelectBody().accept(new SelectVisitorAdapter() {
                @Override
                public void visit(PlainSelect plainSelect) {
                    quoteFromItem(plainSelect.getFromItem(), quote, connection);
                    if (plainSelect.getJoins() != null) {
                        for (Join join : plainSelect.getJoins()) {
                            quoteFromItem(join.getRightItem(), quote, connection);
                        }
                    }
                }
            });
        }

        @Override
        public void visit(Update update) {
            quoteTable(update.getTable(), quote, connection);
        }

        @Override
        public void visit(Delete delete) {
            quoteTable(delete.getTable(), quote, connection);
        }

        @Override
        public void visit(Insert insert) {
            quoteTable(insert.getTable(), quote, connection);
        }
    }
}
