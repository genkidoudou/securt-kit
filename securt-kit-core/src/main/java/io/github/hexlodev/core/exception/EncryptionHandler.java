package io.github.hexlodev.core.exception;

import io.github.hexlodev.core.config.FieldEncryptorProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * 加密/解密异常处理器
 * <p>
 * 提供统一的异常处理策略，支持多种失败处理模式：
 * - FAIL_FAST: 快速失败，抛出异常
 * - FALLBACK: 降级处理，返回原值
 * - RETRY: 重试机制（未来扩展）
 * - SKIP: 跳过该字段
 * </p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Slf4j
public class EncryptionHandler {

    /**
     * 失败处理策略
     */
    public enum FailurePolicy {
        /**
         * 快速失败：抛出异常，中断操作
         */
        FAIL_FAST,
        
        /**
         * 降级处理：使用原始值，记录警告日志
         */
        FALLBACK,
        
        /**
         * 重试：尝试重新执行（当前实现为降级处理）
         */
        RETRY,
        
        /**
         * 跳过：返回 null，跳过该字段
         */
        SKIP
    }

    /**
     * 默认失败策略：FALLBACK（降级处理）
     */
    private static volatile FailurePolicy defaultPolicy = FailurePolicy.FALLBACK;

    /**
     * 设置默认失败策略
     *
     * @param policy 失败策略
     */
    public static void setDefaultPolicy(FailurePolicy policy) {
        if (policy != null) {
            defaultPolicy = policy;
            log.debug("Default failure policy set to: {}", policy);
        }
    }

    /**
     * 获取默认失败策略
     *
     * @return 默认失败策略
     */
    public static FailurePolicy getDefaultPolicy() {
        return defaultPolicy;
    }

    /**
     * 从配置中初始化失败策略
     *
     * @param properties 配置属性
     */
    public static void initFromConfig(FieldEncryptorProperties properties) {
        if (properties != null && properties.getFailurePolicy() != null) {
            defaultPolicy = properties.getFailurePolicy().toHandlerPolicy();
            log.info("Failure policy initialized from config: {}", defaultPolicy);
        }
    }

    /**
     * 处理加密操作的异常
     *
     * @param value          原始值，可以为 null
     * @param tableName      表名，不能为 null 或空白
     * @param fieldName      字段名，不能为 null 或空白
     * @param encryptor      加密函数，不能为 null
     * @param policy         失败策略（如果为 null，使用默认策略）
     * @return 加密后的值，或根据策略返回原值/null
     * @throws IllegalArgumentException 如果表名、字段名或加密函数为 null
     * @throws EncryptionException 如果策略为 FAIL_FAST 且加密失败
     */
    public static String handleEncryption(
            String value, String tableName, String fieldName,
            Supplier<String> encryptor, FailurePolicy policy) {
        
        if (cn.hutool.core.util.StrUtil.isBlank(tableName)) {
            throw new IllegalArgumentException("Table name cannot be null or blank");
        }
        if (cn.hutool.core.util.StrUtil.isBlank(fieldName)) {
            throw new IllegalArgumentException("Field name cannot be null or blank");
        }
        if (encryptor == null) {
            throw new IllegalArgumentException("Encryptor function cannot be null");
        }
        
        if (policy == null) {
            policy = defaultPolicy;
        }

        try {
            return encryptor.get();
        } catch (Exception e) {
            return handleFailure(value, tableName, fieldName, e, policy, true);
        }
    }

    /**
     * 处理解密操作的异常
     *
     * @param encryptedValue 加密后的值，可以为 null
     * @param tableName      表名，不能为 null 或空白
     * @param fieldName      字段名，不能为 null 或空白
     * @param decryptor      解密函数，不能为 null
     * @param policy         失败策略（如果为 null，使用默认策略）
     * @return 解密后的值，或根据策略返回原值/null
     * @throws IllegalArgumentException 如果表名、字段名或解密函数为 null
     * @throws DecryptionException 如果策略为 FAIL_FAST 且解密失败
     */
    public static String handleDecryption(
            String encryptedValue, String tableName, String fieldName,
            Supplier<String> decryptor, FailurePolicy policy) {
        
        if (cn.hutool.core.util.StrUtil.isBlank(tableName)) {
            throw new IllegalArgumentException("Table name cannot be null or blank");
        }
        if (cn.hutool.core.util.StrUtil.isBlank(fieldName)) {
            throw new IllegalArgumentException("Field name cannot be null or blank");
        }
        if (decryptor == null) {
            throw new IllegalArgumentException("Decryptor function cannot be null");
        }
        
        if (policy == null) {
            policy = defaultPolicy;
        }

        try {
            return decryptor.get();
        } catch (Exception e) {
            return handleFailure(encryptedValue, tableName, fieldName, e, policy, false);
        }
    }

    /**
     * 处理失败情况
     *
     * @param value      原始值（加密时为明文，解密时为密文）
     * @param tableName  表名
     * @param fieldName  字段名
     * @param e          异常
     * @param policy     失败策略
     * @param isEncrypt  是否为加密操作（true=加密，false=解密）
     * @return 根据策略返回的值
     */
    private static String handleFailure(
            String value, String tableName, String fieldName,
            Exception e, FailurePolicy policy, boolean isEncrypt) {
        
        String operation = isEncrypt ? "encryption" : "decryption";
        String context = String.format("table=%s, field=%s", tableName, fieldName);

        switch (policy) {
            case FAIL_FAST:
                String errorMsg = String.format("%s failed for %s", operation, context);
                if (isEncrypt) {
                    throw new EncryptionException(errorMsg, e, tableName, fieldName, value);
                } else {
                    throw new DecryptionException(errorMsg, e, tableName, fieldName, value);
                }

            case FALLBACK:
                log.warn("{} failed for {}, using original value [valueLength={}, errorClass={}]. Error: {}", 
                        operation, context,
                        value != null ? value.length() : 0,
                        e.getClass().getSimpleName(),
                        e.getMessage(), e);
                if (log.isDebugEnabled()) {
                    log.debug("Full exception stack for {} failed on {}: {}", operation, context, e);
                }
                return value;

            case RETRY:
                // 当前实现：重试一次后降级处理
                // TODO: 未来版本可以添加真正的重试逻辑（如指数退避、最大重试次数等）
                log.warn("{} failed for {}, retrying once...", operation, context);
                try {
                    // 这里可以添加重试逻辑，目前简单返回原值
                    // 未来可以：重新执行加密/解密操作，支持指数退避等
                    return value;
                } catch (Exception retryEx) {
                    log.warn("Retry failed for {}, falling back to original value", context);
                    return value;
                }

            case SKIP:
                log.warn("{} failed for {}, skipping field [valueLength={}, errorClass={}]. Error: {}", 
                        operation, context,
                        value != null ? value.length() : 0,
                        e.getClass().getSimpleName(),
                        e.getMessage(), e);
                if (log.isDebugEnabled()) {
                    log.debug("Full exception stack for {} failed on {}: {}", operation, context, e);
                }
                return null;

            default:
                log.warn("Unknown failure policy: {}, using FALLBACK", policy);
                return value;
        }
    }
}

