package io.github.genkidoudou.core.strategy;

/**
 * 字段加密解密策略接口
 * 
 * <p>该接口定义了字段加密和解密的核心方法，所有加密策略实现都必须实现此接口。
 * 通过策略模式，允许用户为不同的字段配置不同的加密算法和密钥。</p>
 * 
 * <p>主要功能：</p>
 * <ul>
 *   <li>提供统一的加密和解密方法</li>
 *   <li>支持自定义加密算法实现</li>
 *   <li>支持字段级别的加密策略配置</li>
 * </ul>
 * 
 * <p>实现要求：</p>
 * <ul>
 *   <li>加密和解密方法必须互逆（加密后再解密应得到原始值）</li>
 *   <li>必须处理 null 值（通常返回 null）</li>
 *   <li>应该处理异常情况，避免抛出未检查异常</li>
 *   <li>建议实现为线程安全的</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * @Component
 * public class AESFieldEncryptorStrategy implements FieldEncryptorStrategy {
 *     
 *     @Override
 *     public String encryption(String oldValue) {
 *         if (oldValue == null) {
 *             return null;
 *         }
 *         // 实现 AES 加密逻辑
 *         return AESUtil.encrypt(oldValue);
 *     }
 *     
 *     @Override
 *     public String decryption(String oldValue) {
 *         if (oldValue == null) {
 *             return null;
 *         }
 *         // 实现 AES 解密逻辑
 *         return AESUtil.decrypt(oldValue);
 *     }
 * }
 * }</pre>
 * 
 * <p>配置方式：</p>
 * <pre>{@code
 * securtkit:
 *   encryptor:
 *     tables:
 *       - table-name: user
 *         fields:
 *           - field-name: phone
 *             strategy: com.example.AESFieldEncryptorStrategy  # 指定策略类名
 * }</pre>
 * 
 * @author luyanan
 * @since 2025/10/7
 * @see io.github.genkidoudou.core.cache.StrategyCache
 * @see io.github.genkidoudou.core.exception.EncryptionHandler
 */
public interface FieldEncryptorStrategy {

    /**
     * 加密方法
     * 
     * <p>对原始字段值进行加密处理，返回加密后的字符串。
     * 加密后的值将存储在数据库中。</p>
     * 
     * <p>实现要求：</p>
     * <ul>
     *   <li>如果输入为 null，应返回 null</li>
     *   <li>加密结果应该是字符串类型，通常使用 Base64 编码</li>
     *   <li>应该处理异常情况，避免抛出未检查异常</li>
     *   <li>加密结果应该具有确定性（相同输入产生相同输出）或随机性（每次加密结果不同但可解密），取决于算法</li>
     * </ul>
     * 
     * <p>性能建议：</p>
     * <ul>
     *   <li>该方法会在每次 INSERT/UPDATE 操作时调用，应该保证良好的性能</li>
     *   <li>避免在方法中执行耗时操作（如网络请求、文件 IO 等）</li>
     * </p>
     *
     * @param oldValue 原始字段值（明文），可以为 null
     * @return 加密后的值（密文），如果输入为 null 则返回 null
     * @throws RuntimeException 如果加密失败（框架会使用异常处理器处理）
     * @since 2025/10/7
     */
    String encryption(String oldValue);


    /**
     * 解密方法
     * 
     * <p>对加密后的字段值进行解密处理，返回解密后的原始值。
     * 解密后的值将返回给应用程序使用。</p>
     * 
     * <p>实现要求：</p>
     * <ul>
     *   <li>如果输入为 null，应返回 null</li>
     *   <li>必须能够正确解密由 {@link #encryption(String)} 方法加密的值</li>
     *   <li>应该处理异常情况，避免抛出未检查异常</li>
     *   <li>对于无法解密的无效密文，应该返回 null 或抛出异常（取决于业务需求）</li>
     * </ul>
     * 
     * <p>性能建议：</p>
     * <ul>
     *   <li>该方法会在每次 SELECT 查询时调用，应该保证良好的性能</li>
     *   <li>避免在方法中执行耗时操作（如网络请求、文件 IO 等）</li>
     * </p>
     *
     * @param oldValue 加密后的字段值（密文），可以为 null
     * @return 解密后的值（明文），如果输入为 null 则返回 null
     * @throws RuntimeException 如果解密失败（框架会使用异常处理器处理）
     * @since 2025/10/7
     */
    String decryption(String oldValue);

    default boolean supportsDigest() {
        return false;
    }

    default String digest(java.util.Map<String, String> sourcePlainValues) {
        return null;
    }

    default boolean verifyDigest(java.util.Map<String, String> sourcePlainValues, String digestValue) {
        if (digestValue == null || sourcePlainValues == null) {
            return false;
        }
        String expected = digest(sourcePlainValues);
        if (expected == null) {
            return false;
        }
        byte[] a = expected.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = digestValue.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(a, b);
    }
}
