package io.github.genkidoudou.core.strategy.like;

/**
 * LIKE 模式处理结果
 *
 * @author hexlodev
 * @since 1.2.0
 */
public final class LikeHandleResult {

    /**
     * 处理动作
     */
    public enum Action {
        /**
         * 使用 {@link #getValueForEncrypt()} 作为明文，再走字段加密策略
         */
        ENCRYPT,
        /**
         * 不加密，参数保持原样（常用于无法安全处理的模糊模式）
         */
        SKIP,
        /**
         * 拒绝本次绑定（由调用方抛出异常）
         */
        REJECT
    }

    private final Action action;
    private final String valueForEncrypt;
    private final String message;

    private LikeHandleResult(Action action, String valueForEncrypt, String message) {
        this.action = action;
        this.valueForEncrypt = valueForEncrypt;
        this.message = message;
    }

    /**
     * 按精确值加密（通常等于去掉通配符后的完整串，或原始精确串）
     */
    public static LikeHandleResult encrypt(String plainValue) {
        return new LikeHandleResult(Action.ENCRYPT, plainValue, null);
    }

    /**
     * 跳过加密，保留原始 LIKE 参数
     */
    public static LikeHandleResult skip(String reason) {
        return new LikeHandleResult(Action.SKIP, null, reason);
    }

    /**
     * 拒绝处理
     */
    public static LikeHandleResult reject(String reason) {
        return new LikeHandleResult(Action.REJECT, null, reason);
    }

    public Action getAction() {
        return action;
    }

    public String getValueForEncrypt() {
        return valueForEncrypt;
    }

    public String getMessage() {
        return message;
    }
}
