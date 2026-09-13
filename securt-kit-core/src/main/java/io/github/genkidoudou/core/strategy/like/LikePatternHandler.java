package io.github.genkidoudou.core.strategy.like;

/**
 * LIKE 模式处理接口
 *
 * <p>当 SQL 中出现 {@code encrypted_col LIKE ?} 时，在字段加密之前先调用本接口，
 * 由实现决定如何处理通配符模式。默认实现为 {@link ExactMatchLikePatternHandler}（仅支持无 {@code %} / {@code _} 的精确匹配）。</p>
 *
 * <p>扩展示例（模糊检索旁路）可自行实现本接口，并通过配置
 * {@code securtkit.encryptor.like-pattern-handler} 指定全限定类名。</p>
 *
 * @author hexlodev
 * @since 1.2.0
 * @see ExactMatchLikePatternHandler
 * @see LikePatternHandlerHolder
 */
public interface LikePatternHandler {

    /**
     * 处理 LIKE 模式串
     *
     * @param pattern 业务侧传入的 LIKE 模式（可能含 {@code %} / {@code _}），可为 null
     * @param context 表/字段/数据源等上下文，不为 null
     * @return 处理结果，不能为 null
     */
    LikeHandleResult handle(String pattern, LikeHandleContext context);
}
