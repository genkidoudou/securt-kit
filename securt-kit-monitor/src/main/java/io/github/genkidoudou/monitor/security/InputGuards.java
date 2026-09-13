package io.github.genkidoudou.monitor.security;

import java.util.regex.Pattern;

/**
 * 输入校验工具类
 *
 * <p>替代原先基于 {@code javax.validation} 的 {@code @SafeInput} 注解，
 * 使监控模块不再依赖任何校验框架。所有 {@code check*} 方法在校验通过时返回
 * {@code null}，校验失败时返回可直接展示给前端的错误消息。</p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
public final class InputGuards {

    /**
     * 普通文本最大长度（10KB）
     */
    public static final int MAX_TEXT_LENGTH = 10 * 1024;

    /**
     * SQL 语句最大长度（50KB）
     */
    public static final int MAX_SQL_LENGTH = 50 * 1024;

    /**
     * 标识符（表名 / 字段名 / 数据源标识）最大长度
     */
    public static final int MAX_IDENTIFIER_LENGTH = 64;

    /**
     * 策略类名最大长度
     */
    public static final int MAX_STRATEGY_LENGTH = 200;

    /**
     * WHERE 条件最大长度
     */
    public static final int MAX_WHERE_LENGTH = 1000;

    /**
     * 标准标识符：字母或下划线开头，后接字母、数字、下划线
     */
    public static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /**
     * 表名：允许可选的双引号包裹以及 schema 前缀（如 {@code "public.user"}）
     */
    public static final Pattern TABLE_NAME_PATTERN =
            Pattern.compile("^\"?[a-zA-Z_][a-zA-Z0-9_.]*\"?$");

    /**
     * 策略类名（全限定名）
     */
    public static final Pattern STRATEGY_CLASS_PATTERN =
            Pattern.compile("^[a-zA-Z][a-zA-Z0-9_.]*$");

    /**
     * HTML 标签 / JavaScript 代码特征
     */
    private static final Pattern HTML_PATTERN = Pattern.compile(
            "<[^>]*>|javascript:|on\\w+\\s*=",
            Pattern.CASE_INSENSITIVE
    );

    private InputGuards() {
    }

    /**
     * 字符串是否非空（非 null 且去除首尾空白后长度大于 0）
     */
    public static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 是否超过指定长度
     */
    public static boolean exceedsLength(String value, int maxLength) {
        return value != null && value.length() > maxLength;
    }

    /**
     * 是否包含 HTML 标签或 JavaScript 代码
     */
    public static boolean containsHtml(String value) {
        return value != null && HTML_PATTERN.matcher(value).find();
    }

    /**
     * 是否匹配指定正则
     */
    public static boolean matches(String value, Pattern pattern) {
        return value != null && pattern != null && pattern.matcher(value).matches();
    }

    /**
     * 校验必填项
     *
     * @param value 值
     * @param label 字段展示名
     * @return 错误消息，校验通过返回 {@code null}
     */
    public static String checkRequired(String value, String label) {
        if (!hasText(value)) {
            return label + "不能为空";
        }
        return null;
    }

    /**
     * 校验长度
     *
     * @param value     值
     * @param label     字段展示名
     * @param maxLength 最大长度
     * @return 错误消息，校验通过返回 {@code null}
     */
    public static String checkLength(String value, String label, int maxLength) {
        if (exceedsLength(value, maxLength)) {
            return label + "长度不能超过 " + maxLength + " 字符";
        }
        return null;
    }

    /**
     * 校验不包含 HTML 标签 / JavaScript 代码
     *
     * @param value 值
     * @param label 字段展示名
     * @return 错误消息，校验通过返回 {@code null}
     */
    public static String checkNoHtml(String value, String label) {
        if (containsHtml(value)) {
            return label + "不能包含 HTML 标签或 JavaScript 代码";
        }
        return null;
    }

    /**
     * 校验格式
     *
     * @param value   值
     * @param label   字段展示名
     * @param pattern 正则
     * @return 错误消息，校验通过返回 {@code null}
     */
    public static String checkPattern(String value, String label, Pattern pattern) {
        if (value != null && !matches(value, pattern)) {
            return label + "格式不正确";
        }
        return null;
    }

    /**
     * 校验自由文本：必填 + 长度 + 无 HTML
     *
     * @param value     值
     * @param label     字段展示名
     * @param maxLength 最大长度
     * @param required  是否必填
     * @return 错误消息，校验通过返回 {@code null}
     */
    public static String checkText(String value, String label, int maxLength, boolean required) {
        if (required) {
            String error = checkRequired(value, label);
            if (error != null) {
                return error;
            }
        }
        if (value == null) {
            return null;
        }
        String error = checkLength(value, label, maxLength);
        if (error != null) {
            return error;
        }
        return checkNoHtml(value, label);
    }

    /**
     * 校验标识符（字段名 / 数据源标识）：长度 64 + 标识符格式
     *
     * @param value    值
     * @param label    字段展示名
     * @param required 是否必填
     * @return 错误消息，校验通过返回 {@code null}
     */
    public static String checkIdentifier(String value, String label, boolean required) {
        if (required) {
            String error = checkRequired(value, label);
            if (error != null) {
                return error;
            }
        }
        if (!hasText(value)) {
            return null;
        }
        String error = checkLength(value, label, MAX_IDENTIFIER_LENGTH);
        if (error != null) {
            return error;
        }
        return checkPattern(value.trim(), label, IDENTIFIER_PATTERN);
    }

    /**
     * 校验表名：长度 64 + 允许双引号包裹与 schema 前缀
     *
     * @param value    值
     * @param label    字段展示名
     * @param required 是否必填
     * @return 错误消息，校验通过返回 {@code null}
     */
    public static String checkTableName(String value, String label, boolean required) {
        if (required) {
            String error = checkRequired(value, label);
            if (error != null) {
                return error;
            }
        }
        if (!hasText(value)) {
            return null;
        }
        String error = checkLength(value, label, MAX_IDENTIFIER_LENGTH);
        if (error != null) {
            return error;
        }
        return checkPattern(value.trim(), label, TABLE_NAME_PATTERN);
    }

    /**
     * 校验策略类名：长度 200 + 全限定类名格式
     *
     * @param value 值
     * @return 错误消息，校验通过返回 {@code null}
     */
    public static String checkStrategyClass(String value) {
        if (!hasText(value)) {
            return null;
        }
        String error = checkLength(value, "策略类名", MAX_STRATEGY_LENGTH);
        if (error != null) {
            return error;
        }
        return checkPattern(value.trim(), "策略类名", STRATEGY_CLASS_PATTERN);
    }

    /**
     * 校验 SQL 语句：必填 + 长度 50KB
     *
     * <p>不做 HTML 检查，因为 SQL 中的比较运算符（{@code <}、{@code >}）
     * 会被 HTML 标签正则误判；SQL 的语法与危险关键字校验由
     * {@link SqlValidator} 负责。</p>
     *
     * @param value 值
     * @return 错误消息，校验通过返回 {@code null}
     */
    public static String checkSql(String value) {
        String error = checkRequired(value, "SQL 语句");
        if (error != null) {
            return error;
        }
        return checkLength(value, "SQL 语句", MAX_SQL_LENGTH);
    }

    /**
     * 校验 WHERE 条件：长度 1000（可选项）
     *
     * @param value 值
     * @return 错误消息，校验通过返回 {@code null}
     */
    public static String checkWhereCondition(String value) {
        return checkLength(value, "WHERE 条件", MAX_WHERE_LENGTH);
    }
}
