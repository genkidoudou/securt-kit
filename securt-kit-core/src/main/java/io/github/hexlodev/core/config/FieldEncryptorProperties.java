package io.github.hexlodev.core.config;

import io.github.hexlodev.core.exception.EncryptionHandler;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * 字段加密配置属性
 * <p>
 * 配置前缀：{@code securtkit.encryptor}
 * </p>
 * <p>
 * 主要配置项：
 * <ul>
 *   <li>enable: 是否开启加密功能（预留）</li>
 *   <li>tables: 按表配置需要加密的字段及策略</li>
 *   <li>failure-policy: 加密/解密失败处理策略（FALLBACK/FAIL_FAST/RETRY/SKIP）</li>
 *   <li>sql-parse-cache: SQL 解析缓存配置</li>
 * </ul>
 * </p>
 * <p>
 * 配置示例（YAML）：
 * <pre>{@code
 * securtkit:
 *   encryptor:
 *     enable: true
 *     failure-policy: FALLBACK
 *     sql-parse-cache:
 *       enable: true
 *       max-size: 1000
 *     tables:
 *       - table-name: user
 *         fields:
 *           - field-name: name
 *           - field-name: phone
 *             strategy: com.example.CustomStrategy
 * }</pre>
 * </p>
 *
 * @author hexlodev
 * @since 1.0.0
 * @see TableConfig
 * @see FieldConfig
 * @see SqlParseCacheConfig
 * @see FailurePolicy
 */
@ConfigurationProperties(prefix = FieldEncryptorProperties.PREFIX)
@Data
public class FieldEncryptorProperties {

    public static final String PREFIX = "securtkit.encryptor";
    /**
     * 是否开启
     * @since 2025/10/7
     */
    private boolean enable;

    /**
     * SQL 解析缓存配置
     * @since 1.0.0
     */
    private SqlParseCacheConfig sqlParseCache;

    /**
     * 加密/解密失败处理策略
     * <p>
     * 当加密或解密操作失败时，框架如何处理：
     * <ul>
     *   <li>FAIL_FAST: 快速失败，立即抛出异常，中断操作</li>
     *   <li>FALLBACK: 降级处理，使用原始值继续执行（默认策略，推荐用于生产环境）</li>
     *   <li>RETRY: 重试机制（当前实现为降级处理）</li>
     *   <li>SKIP: 跳过该字段，返回 null</li>
     * </ul>
     * </p>
     * <p>
     * 配置示例：
     * <pre>{@code
     * securtkit:
     *   encryptor:
     *     failure-policy: FALLBACK
     * }</pre>
     * </p>
     * 
     * @see io.github.hexlodev.core.exception.EncryptionHandler
     * @since 1.0.0
     */
    private FailurePolicy failurePolicy = FailurePolicy.FALLBACK;

    /**
     * 表配置列表
     * <p>
     * 单数据源场景：不指定 datasource-id，应用到所有数据源
     * 多数据源场景：指定 datasource-id 区分不同数据源
     * </p>
     * <p>
     * 配置示例：
     * <pre>{@code
     * # 单数据源场景
     * tables:
     *   - table-name: user
     *     fields:
     *       - field-name: name
     * 
     * # 多数据源场景
     * tables:
     *   - table-name: user
     *     datasource-id: primary
     *     fields:
     *       - field-name: name
     * }</pre>
     * </p>
     * @since 2025/10/8
     */
    private List<TableConfig> tables;


    /**
     * 表配置项：tableName + fields + datasourceId（可选）
     */
    /**
     * 表配置
     * @author luyanan
     * @since 2025/10/8
     */
    @Data
    public static class TableConfig {


        /**
         * 表名
         * @since 2025/10/8
         */

        @NotBlank(message = "表名不能为空")
        private String tableName;

        /**
         * 数据源标识（可选）
         * <p>
         * - 如果为空或 null，则应用到所有数据源（单数据源场景或全局配置）
         * - 如果指定，则只应用到该数据源（多数据源场景）
         * </p>
         * <p>
         * 配置示例：
         * <pre>{@code
         * # 单数据源场景（不指定 datasource-id）
         * - table-name: user
         *   fields: ...
         * 
         * # 多数据源场景（指定 datasource-id）
         * - table-name: user
         *   datasource-id: primary
         *   fields: ...
         * }</pre>
         * </p>
         * @since 1.2.0
         */
        private String datasourceId;

        /**
         * 字段配置列表：fieldName + strategy（可选）
         */
        private List<FieldConfig> fields;

    }


    /**
     * 字段配置：字段名与可选策略类名（全限定名）
     */
    /**
     * 字段配置
     * @since 2025/10/8
     */

    @Data
    public static class FieldConfig {

        /**
         * 字段名称
         * @since 2025/10/8
         */

        private String fieldName;
        /**
         * 策略类名（全限定名）；为空则使用默认策略
         */
        /**
         * 字段加密策略(不配置使用默认的策略)
         * @since 2025/10/8
         */
        private String strategy;
    }

    /**
     * SQL 解析缓存配置
     * @author hexlodev
     * @since 1.0.0
     */
    @Data
    public static class SqlParseCacheConfig {
        /**
         * 是否启用 SQL 解析缓存
         * 默认值：true（启用缓存可以显著提升性能）
         */
        private boolean enable = true;

        /**
         * 缓存最大容量
         * 默认值：1000（最多缓存 1000 条 SQL 解析结果）
         * 建议值：500-2000，根据应用实际 SQL 数量调整
         */
        private int maxSize = 1000;
    }

    /**
     * 失败策略类型（用于配置）
     * <p>
     * 提供字符串到枚举的转换，便于配置文件使用
     * </p>
     */
    public enum FailurePolicy {
        FAIL_FAST,
        FALLBACK,
        RETRY,
        SKIP;

        /**
         * 从字符串转换为枚举
         *
         * @param value 字符串值
         * @return 枚举值，如果无法识别则返回 FALLBACK
         */
        public static FailurePolicy fromString(String value) {
            if (value == null || value.trim().isEmpty()) {
                return FALLBACK;
            }
            try {
                return valueOf(value.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                return FALLBACK;
            }
        }

        /**
         * 转换为 EncryptionHandler.FailurePolicy
         *
         * @return 对应的处理器策略枚举
         */
        public EncryptionHandler.FailurePolicy toHandlerPolicy() {
            return EncryptionHandler.FailurePolicy.valueOf(this.name());
        }
    }

}
