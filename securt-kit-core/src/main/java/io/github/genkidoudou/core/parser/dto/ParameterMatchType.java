package io.github.genkidoudou.core.parser.dto;

/**
 * 参数与列之间的匹配方式
 *
 * <p>用于区分 {@code =} 与 {@code LIKE} 等，以便参数加密时走不同的模式处理策略。</p>
 *
 * @author hexlodev
 * @since 1.2.0
 */
public enum ParameterMatchType {

    /**
     * 等值比较（默认），如 {@code col = ?}
     */
    EQUAL,

    /**
     * LIKE / ILIKE 模式匹配，如 {@code col LIKE ?}
     */
    LIKE
}
