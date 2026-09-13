package io.github.genkidoudou.core.parser.dto;

import io.github.genkidoudou.core.strategy.FieldEncryptorStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段加密信息数据传输对象
 * 
 * <p>该类用于存储查询结果中需要解密的字段信息，主要用于 SELECT 语句的结果集解密场景。</p>
 * 
 * <p>主要用途：</p>
 * <ul>
 *   <li>记录查询结果中的列名（可能是别名）</li>
 *   <li>记录源表名和源字段名（用于查找加密配置）</li>
 *   <li>关联加密策略类</li>
 * </ul>
 * 
 * <p>使用场景：</p>
 * <pre>{@code
 * // SQL: SELECT u.name AS user_name, u.phone FROM user u
 * FieldEncryptorInfoDto nameDto = FieldEncryptorInfoDto.builder()
 *     .columnName("user_name")  // 查询结果中的列名（别名）
 *     .sourceTableName("user")   // 源表名
 *     .sourceColumn("name")      // 源字段名
 *     .fieldEncryptor(AESStrategy.class)  // 加密策略
 *     .build();
 *     
 * FieldEncryptorInfoDto phoneDto = FieldEncryptorInfoDto.builder()
 *     .columnName("phone")       // 查询结果中的列名（无别名时使用字段名）
 *     .sourceTableName("user")   // 源表名
 *     .sourceColumn("phone")     // 源字段名
 *     .fieldEncryptor(AESStrategy.class)  // 加密策略
 *     .build();
 * }</pre>
 * 
 * @author hexlodev
 * @since 1.0.0
 * @see ColumnTableDto
 * @see FieldEncryptorStrategy
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldEncryptorInfoDto {

    /**
     * 列名（查询结果中的字段名或别名）
     * 
     * <p>这是 ResultSet 中实际的列名，用于匹配查询结果中的字段。</p>
     * 
     * <p>示例：</p>
     * <ul>
     *   <li>SQL: SELECT name FROM user → columnName = "name"</li>
     *   <li>SQL: SELECT name AS user_name FROM user → columnName = "user_name"</li>
     *   <li>SQL: SELECT u.name FROM user u → columnName = "name"（如果驱动支持）</li>
     * </ul>
     * 
     * <p>不能为 null 或空白，否则无法匹配查询结果</p>
     */
    private String columnName;

    /**
     * 源列名（实际数据库表中的字段名，小写）
     * 
     * <p>这是数据库中实际存在的字段名，用于查找加密配置。
     * 如果字段名在配置中为 "phone"，则此字段应该为 "phone"（小写）。</p>
     * 
     * <p>不能为 null 或空白，否则无法查找加密配置</p>
     */
    private String sourceColumn;

    /**
     * 源表名（实际数据库表名，小写）
     * 
     * <p>这是数据库中实际存在的表名，用于查找加密配置。
     * 如果表名在配置中为 "user"，则此字段应该为 "user"（小写）。</p>
     * 
     * <p>不能为 null 或空白，否则无法查找加密配置</p>
     */
    private String sourceTableName;

    /**
     * 字段加密策略类
     * 
     * <p>该字段对应的加密策略类的 Class 对象，用于获取策略实例并执行解密操作。</p>
     * 
     * <p>如果为 null，表示该字段不需要加密，或使用默认策略</p>
     * 
     * @see FieldEncryptorStrategy
     * @see io.github.genkidoudou.core.cache.StrategyCache
     */
    private Class<? extends FieldEncryptorStrategy> fieldEncryptor;

    /**
     * SELECT 投影列序（1-based），供 ResultSet 列标签无法匹配表达式时按 ordinal 回退。
     */
    private Integer resultColumnIndex;
}
