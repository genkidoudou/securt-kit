package io.github.genkidoudou.core.strategy.like;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局 LIKE 模式处理器持有者
 *
 * @author hexlodev
 * @since 1.2.0
 */
@Slf4j
public final class LikePatternHandlerHolder {

    private static volatile LikePatternHandler HANDLER = new ExactMatchLikePatternHandler();

    private LikePatternHandlerHolder() {
    }

    /**
     * 使用配置类名初始化；空白则使用 {@link ExactMatchLikePatternHandler}
     *
     * @param handlerClassName 实现类全名，可为 null/blank
     */
    public static void init(String handlerClassName) {
        if (StrUtil.isBlank(handlerClassName)) {
            HANDLER = new ExactMatchLikePatternHandler();
            log.debug("LikePatternHandler set to default ExactMatchLikePatternHandler");
            return;
        }
        try {
            Class<?> clazz = Class.forName(handlerClassName.trim());
            if (!LikePatternHandler.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException(
                        "like-pattern-handler must implement LikePatternHandler: " + handlerClassName);
            }
            @SuppressWarnings("unchecked")
            Class<? extends LikePatternHandler> handlerClass = (Class<? extends LikePatternHandler>) clazz;
            LikePatternHandler instance = tryGetFromSpring(handlerClass);
            if (instance == null) {
                instance = handlerClass.getDeclaredConstructor().newInstance();
            }
            HANDLER = instance;
            log.info("LikePatternHandler initialized: {}", handlerClass.getName());
        } catch (Exception e) {
            log.error("Failed to init LikePatternHandler [{}], fallback to ExactMatchLikePatternHandler",
                    handlerClassName, e);
            HANDLER = new ExactMatchLikePatternHandler();
        }
    }

    private static LikePatternHandler tryGetFromSpring(Class<? extends LikePatternHandler> clazz) {
        try {
            return SpringUtil.getBean(clazz);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 手动注册（测试或非 Spring 环境）
     */
    public static void setHandler(LikePatternHandler handler) {
        HANDLER = handler != null ? handler : new ExactMatchLikePatternHandler();
    }

    public static LikePatternHandler getHandler() {
        LikePatternHandler h = HANDLER;
        return h != null ? h : new ExactMatchLikePatternHandler();
    }

    /**
     * 重置为默认实现
     */
    public static void reset() {
        HANDLER = new ExactMatchLikePatternHandler();
    }
}
