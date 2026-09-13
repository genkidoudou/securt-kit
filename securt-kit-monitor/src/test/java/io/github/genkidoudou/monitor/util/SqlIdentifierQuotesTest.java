package io.github.genkidoudou.monitor.util;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlIdentifierQuotesTest {

    @Test
    void quoteIdentifier_wrapsBareName() {
        assertEquals("\"user\"", SqlIdentifierQuotes.quoteIdentifier("\"", "user"));
        assertEquals("`order`", SqlIdentifierQuotes.quoteIdentifier("`", "order"));
    }

    @Test
    void quoteIdentifier_keepsAlreadyQuoted() {
        assertEquals("\"user\"", SqlIdentifierQuotes.quoteIdentifier("\"", "\"user\""));
    }

    @Test
    void quoteTableIdentifiers_quotesUserInSelect() {
        String sql = "SELECT * FROM user LIMIT 10";
        String rewritten = SqlIdentifierQuotes.quoteTableIdentifiers(sql, "\"");
        assertTrue(rewritten.contains("\"user\"") || rewritten.contains("\"USER\""), rewritten);
        assertEquals(false, rewritten.matches("(?i).*\\bFROM\\s+user\\b.*"), rewritten);
    }

    @Test
    void quoteTableIdentifiers_preservesAlreadyQuoted() {
        String sql = "SELECT * FROM \"user\" LIMIT 10";
        String rewritten = SqlIdentifierQuotes.quoteTableIdentifiers(sql, "\"");
        assertTrue(rewritten.contains("\"user\""), rewritten);
    }

    @Test
    void quoteTableIdentifiers_preservesLeadingSkipComment() {
        String sql = "/* SECURT_SKIP */ SELECT * FROM digest_user LIMIT 10";

        String rewritten = SqlIdentifierQuotes.quoteTableIdentifiers(sql, "\"");

        assertTrue(rewritten.startsWith("/* SECURT_SKIP */"), rewritten);
    }

    @Test
    void quoteTableIdentifiersWithConnection_preservesLeadingSkipComment() throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:h2:mem:monitor_quote_skip;MODE=MySQL;DB_CLOSE_DELAY=-1")) {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE digest_user (id BIGINT PRIMARY KEY, phone VARCHAR(20))");
            }

            String rewritten = SqlIdentifierQuotes.quoteTableIdentifiers(
                    c, "/* SECURT_SKIP */ SELECT * FROM digest_user LIMIT 10");

            assertTrue(rewritten.startsWith("/* SECURT_SKIP */"), rewritten);
        }
    }

    @Test
    void h2QuotedTableUnquotedPk_resolvedOrderByWorks() throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:h2:mem:monitor_quote_case;MODE=MySQL;DB_CLOSE_DELAY=-1")) {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE \"user\" (id BIGINT PRIMARY KEY, name VARCHAR(20))");
                st.execute("INSERT INTO \"user\" (id, name) VALUES (1, 'a')");
            }
            String wrong = "SELECT * FROM \"user\" ORDER BY \"id\" LIMIT 10";
            boolean wrongFailed = false;
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(wrong)) {
                rs.next();
            } catch (Exception e) {
                wrongFailed = true;
            }
            assertTrue(wrongFailed, "quoted lowercase id should fail on H2 unquoted columns");

            String pkQ = SqlIdentifierQuotes.quoteResolvedColumn(c, "user", "id");
            String sql = "SELECT * FROM \"user\" ORDER BY " + pkQ + " LIMIT 10";
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                assertTrue(rs.next());
                assertEquals(1L, rs.getLong(1));
            }
        }
    }

    @Test
    void h2UnquotedTable_resolvedPhysicalNameWorks() throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:h2:mem:monitor_quote_table;MODE=MySQL;DB_CLOSE_DELAY=-1")) {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE digest_user (id BIGINT PRIMARY KEY, phone VARCHAR(20))");
                st.execute("INSERT INTO digest_user (id, phone) VALUES (1, '138')");
            }
            boolean wrongFailed = false;
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM \"digest_user\" LIMIT 10")) {
                rs.next();
            } catch (Exception e) {
                wrongFailed = true;
            }
            assertTrue(wrongFailed, "quoted lowercase digest_user should fail when created unquoted");

            String tableQ = SqlIdentifierQuotes.quoteResolvedTable(c, "digest_user");
            String rewritten = SqlIdentifierQuotes.quoteTableIdentifiers(c, "SELECT * FROM digest_user LIMIT 10");
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(rewritten)) {
                assertTrue(rs.next());
                assertEquals(1L, rs.getLong(1));
            }
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM " + tableQ + " LIMIT 10")) {
                assertTrue(rs.next());
            }
        }
    }
}
