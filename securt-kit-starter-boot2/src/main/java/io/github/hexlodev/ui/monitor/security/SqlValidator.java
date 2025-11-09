package io.github.hexlodev.ui.monitor.security;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SQL 验证工具类
 * 用于验证和清理 SQL 语句，防止 SQL 注入攻击
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Slf4j
public class SqlValidator {

    /**
     * SQL 语句最大长度（50KB）
     */
    private static final int MAX_SQL_LENGTH = 50 * 1024;

    /**
     * 危险 SQL 关键字（用于非查询场景）
     */
    private static final Set<String> DANGEROUS_KEYWORDS = new HashSet<>(Arrays.asList(
        "DROP", "TRUNCATE", "ALTER", "CREATE", "GRANT", "REVOKE",
        "EXEC", "EXECUTE", "SP_", "XP_"
    ));

    /**
     * 验证 SQL 语句是否安全
     *
     * @param sql SQL 语句
     * @param allowDML 是否允许 DML 语句（INSERT、UPDATE、DELETE）
     * @return 验证结果
     */
    public static ValidationResult validateSql(String sql, boolean allowDML) {
        if (sql == null || sql.trim().isEmpty()) {
            return ValidationResult.error("SQL 语句不能为空");
        }

        // 1. 长度验证
        if (sql.length() > MAX_SQL_LENGTH) {
            return ValidationResult.error("SQL 语句过长，最大允许 " + MAX_SQL_LENGTH + " 字符");
        }

        // 2. SQL 语法验证
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);

            // 3. 语句类型验证
            if (!allowDML && !(statement instanceof Select)) {
                return ValidationResult.error("只支持 SELECT 查询语句");
            }

            // 4. 危险关键字检查（对于非查询场景）
            if (allowDML) {
                String upperSql = sql.toUpperCase();
                for (String keyword : DANGEROUS_KEYWORDS) {
                    if (upperSql.contains(keyword)) {
                        return ValidationResult.error("SQL 语句包含危险关键字: " + keyword);
                    }
                }
            }

            return ValidationResult.success();

        } catch (JSQLParserException e) {
            log.warn("SQL 语法验证失败: {}", e.getMessage());
            return ValidationResult.error("SQL 语法错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("SQL 验证异常", e);
            return ValidationResult.error("SQL 验证失败: " + e.getMessage());
        }
    }

    /**
     * 验证 SQL 查询语句（只允许 SELECT）
     */
    public static ValidationResult validateSelectSql(String sql) {
        return validateSql(sql, false);
    }

    /**
     * 验证 SQL DML 语句（允许 INSERT、UPDATE、DELETE、SELECT）
     */
    public static ValidationResult validateDmlSql(String sql) {
        return validateSql(sql, true);
    }

    /**
     * 验证结果
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}

