package io.github.genkidoudou.core.exception;

import io.github.genkidoudou.core.config.FieldEncryptorProperties;
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
     * <p>
     * 根据失败策略统一处理加密/解密失败的情况，提供一致的异常信息和日志格式。
     * </p>
     *
     * @param value      原始值（加密时为明文，解密时为密文）
     * @param tableName  表名，不能为 null
     * @param fieldName  字段名，不能为 null
     * @param e          异常，不能为 null
     * @param policy     失败策略，不能为 null
     * @param isEncrypt  是否为加密操作（true=加密，false=解密）
     * @return 根据策略返回的值（FALLBACK 返回原值，SKIP 返回 null）
     * @throws EncryptionException 如果策略为 FAIL_FAST 且是加密操作
     * @throws DecryptionException 如果策略为 FAIL_FAST 且是解密操作
     */
    private static String handleFailure(
            String value, String tableName, String fieldName,
            Exception e, FailurePolicy policy, boolean isEncrypt) {
        
        String operation = isEncrypt ? "encryption" : "decryption";
        String context = String.format("table=%s, field=%s", tableName, fieldName);
        int valueLength = value != null ? value.length() : 0;
        String errorClass = e.getClass().getSimpleName();
        String errorMessage = e.getMessage();

        switch (policy) {
            case FAIL_FAST:
                // 快速失败：抛出异常，中断操作
                String errorMsg = String.format("%s failed for %s: %s", operation, context, errorMessage);
                if (isEncrypt) {
                    throw new EncryptionException(errorMsg, e, tableName, fieldName, value);
                } else {
                    throw new DecryptionException(errorMsg, e, tableName, fieldName, value);
                }

            case FALLBACK:
                // 降级处理：使用原始值，记录警告日志
                log.warn("[{} FAILED] {} for {}, using original value [valueLength={}, errorClass={}]. Error: {}", 
                        operation.toUpperCase(), operation, context, valueLength, errorClass, errorMessage, e);
                if (log.isDebugEnabled()) {
                    log.debug("[{} FAILED] Full exception stack for {} on {}: {}", 
                            operation.toUpperCase(), operation, context, e);
                }
                return value;

            case RETRY:
                // 重试：当前实现为降级处理，未来可以添加真正的重试逻辑
                log.warn("[{} RETRY] {} failed for {}, retrying once...", 
                        operation.toUpperCase(), operation, context);
                try {
                    // TODO: 未来版本可以添加真正的重试逻辑（如指数退避、最大重试次数等）
                    // 当前实现：简单返回原值
                    return value;
                } catch (Exception retryEx) {
                    log.warn("[{} RETRY FAILED] Retry failed for {}, falling back to original value. Error: {}", 
                            operation.toUpperCase(), context, retryEx.getMessage(), retryEx);
                    return value;
                }

            case SKIP:
                // 跳过：返回 null，跳过该字段
                log.warn("[{} SKIP] {} failed for {}, skipping field [valueLength={}, errorClass={}]. Error: {}", 
                        operation.toUpperCase(), operation, context, valueLength, errorClass, errorMessage, e);
                if (log.isDebugEnabled()) {
                    log.debug("[{} SKIP] Full exception stack for {} on {}: {}", 
                            operation.toUpperCase(), operation, context, e);
                }
                return null;

            default:
                // 未知策略：使用 FALLBACK 作为默认策略
                log.warn("[{} UNKNOWN POLICY] Unknown failure policy: {}, using FALLBACK", 
                        operation.toUpperCase(), policy);
                return value;
        }
    }
}

