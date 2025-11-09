package io.github.test;

import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
import org.springframework.stereotype.Component;

/**
 * 自定义字段加密策略实现类
 * 
 * <p>这是一个简单的加密策略实现示例，用于演示和测试。</p>
 * 
 * <p><b>加密方式：</b></p>
 * <ul>
 *   <li><b>加密</b>：在原始值后追加 <code>"(加密)"</code> 前缀</li>
 *   <li><b>解密</b>：移除 <code>"(加密)"</code> 前缀</li>
 * </ul>
 * 
 * <p><b>示例：</b></p>
 * <pre>{@code
 * // 加密
 * String plaintext = "张三";
 * String ciphertext = strategy.encryption(plaintext);
 * // 结果：ciphertext = "张三(加密)"
 * 
 * // 解密
 * String decrypted = strategy.decryption(ciphertext);
 * // 结果：decrypted = "张三"
 * }</pre>
 * 
 * <p><b>注意事项：</b></p>
 * <ul>
 *   <li>⚠️ <b>此实现仅用于测试和演示，生产环境请使用真正的加密算法（如 AES）</b></li>
 *   <li>此实现不是真正的加密，只是简单的字符串操作</li>
 *   <li>生产环境应该使用强加密算法（如 AES-256）</li>
 *   <li>密钥应该从配置中心或环境变量读取，不能硬编码</li>
 * </ul>
 * 
 * <p><b>生产环境推荐实现：</b></p>
 * <pre>{@code
 * @Component
 * public class AESFieldEncryptorStrategy implements FieldEncryptorStrategy {
 *     
 *     private static final String ALGORITHM = "AES";
 *     private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
 *     
 *     // 从配置中心或环境变量读取密钥
 *     @Value("${encrypt.key}")
 *     private String secretKey;
 *     
 *     @Override
 *     public String encryption(String oldValue) {
 *         if (oldValue == null) {
 *             return null;
 *         }
 *         try {
 *             Cipher cipher = Cipher.getInstance(TRANSFORMATION);
 *             SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), ALGORITHM);
 *             cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
 *             byte[] encrypted = cipher.doFinal(oldValue.getBytes(StandardCharsets.UTF_8));
 *             return Base64.getEncoder().encodeToString(encrypted);
 *         } catch (Exception e) {
 *             throw new RuntimeException("加密失败", e);
 *         }
 *     }
 *     
 *     @Override
 *     public String decryption(String oldValue) {
 *         if (oldValue == null) {
 *             return null;
 *         }
 *         try {
 *             Cipher cipher = Cipher.getInstance(TRANSFORMATION);
 *             SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), ALGORITHM);
 *             cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
 *             byte[] decrypted = cipher.decrypt(Base64.getDecoder().decode(oldValue));
 *             return new String(decrypted, StandardCharsets.UTF_8);
 *         } catch (Exception e) {
 *             throw new RuntimeException("解密失败", e);
 *         }
 *     }
 * }
 * }</pre>
 * 
 * @author hexlodev
 * @since 1.1.0
 * @see io.github.hexlodev.core.strategy.FieldEncryptorStrategy
 */
@Component
public class MyFieldEncryptorStrategy implements FieldEncryptorStrategy {

    /**
     * 加密前缀（用于标识加密后的值）
     * 
     * <p>注意：这只是测试用的简单实现，生产环境请使用真正的加密算法。</p>
     */
    private static final String prefix = "(加密)";

    /**
     * 加密方法
     * 
     * <p>在原始值后追加加密前缀。</p>
     * 
     * @param oldValue 原始值（明文）
     * @return 加密后的值（密文）
     */
    @Override
    public String encryption(String oldValue) {
        if (oldValue == null) {
            return null;
        }
        return oldValue + prefix;
    }

    /**
     * 解密方法
     * 
     * <p>移除加密前缀，恢复原始值。</p>
     * 
     * @param oldValue 加密后的值（密文）
     * @return 解密后的值（明文）
     */
    @Override
    public String decryption(String oldValue) {
        if (oldValue == null) {
            return null;
        }
        return oldValue.replace(prefix, "");
    }
}
