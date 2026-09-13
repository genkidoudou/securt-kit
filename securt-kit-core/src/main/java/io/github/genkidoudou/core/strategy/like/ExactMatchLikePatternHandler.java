package io.github.genkidoudou.core.strategy.like;

import lombok.extern.slf4j.Slf4j;

/**
 * LIKE 默认实现：精确匹配
 *
 * <p>规则：</p>
 * <ul>
 *   <li>模式中不含通配符 {@code %} / {@code _}：按完整字符串加密（等价于 {@code col = ?}）</li>
 *   <li>含通配符：不加密并打日志（避免把 {@code %张%} 整串加密导致永远查不到）；
 *       如需模糊检索请自定义 {@link LikePatternHandler}</li>
 * </ul>
 *
 * @author hexlodev
 * @since 1.2.0
 */
@Slf4j
public class ExactMatchLikePatternHandler implements LikePatternHandler {

    @Override
    public LikeHandleResult handle(String pattern, LikeHandleContext context) {
        if (pattern == null) {
            return LikeHandleResult.skip("LIKE pattern is null");
        }

        if (containsWildcard(pattern)) {
            String msg = String.format(
                    "LIKE pattern contains wildcards, ExactMatchLikePatternHandler skips encryption "
                            + "[table=%s, column=%s, datasourceId=%s, parameterIndex=%d]. "
                            + "Use a custom LikePatternHandler for fuzzy search.",
                    context != null ? context.getTableName() : null,
                    context != null ? context.getColumnName() : null,
                    context != null ? context.getDatasourceId() : null,
                    context != null ? context.getParameterIndex() : -1);
            log.warn(msg);
            return LikeHandleResult.skip(msg);
        }

        return LikeHandleResult.encrypt(pattern);
    }

    /**
     * 是否包含 SQL LIKE 通配符（简化：不解析 ESCAPE 子句，命中 {@code %} 或 {@code _} 即视为模糊）
     */
    public static boolean containsWildcard(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }
        return pattern.indexOf('%') >= 0 || pattern.indexOf('_') >= 0;
    }
}
