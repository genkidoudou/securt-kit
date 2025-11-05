package io.github.hexlodev.core.parser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表字段信息与占位符映射承载体
 * 
 * <p>该类用于存储 SQL 解析后的字段信息，建立 PreparedStatement 占位符（?）与数据库表字段的映射关系。
 * 主要用于 INSERT、UPDATE 语句的参数加密场景。</p>
 * 
 * <p>主要用途：</p>
 * <ul>
 *   <li>记录占位符对应的表名和字段名</li>
 *   <li>记录 INSERT 语句中字段的位置索引</li>
 *   <li>支持表别名和真实表名的映射</li>
 *   <li>区分字段是否来自真实表（而非子查询）</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * // INSERT 语句：INSERT INTO user(name, phone) VALUES(?, ?)
 * ColumnTableDto nameDto = ColumnTableDto.builder()
 *     .sourceTableName("user")
 *     .sourceColumn("name")
 *     .insertFieldIndex(0)  // 第一个占位符
 *     .fromSourceTable(true)
 *     .build();
 *     
 * ColumnTableDto phoneDto = ColumnTableDto.builder()
 *     .sourceTableName("user")
 *     .sourceColumn("phone")
 *     .insertFieldIndex(1)  // 第二个占位符
 *     .fromSourceTable(true)
 *     .build();
 * }</pre>
 * 
 * @author hexlodev
 * @since 1.0.0
 * @see FieldEncryptorInfoDto
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnTableDto {
    /**
     * 字段所属的表的别名（FROM/JOIN 子句中的表别名）
     * 
     * <p>例如：SELECT u.name FROM user u，这里的别名是 "u"</p>
     * 
     * <p>可以为 null，表示没有使用别名</p>
     */
    private String tableAliasName;

    /**
     * 字段所属表的真实表名（小写）
     * 
     * <p>这是数据库中实际存在的表名，用于查找加密配置。
     * 如果表名在配置中为 "user"，则此字段应该为 "user"（小写）。</p>
     * 
     * <p>不能为 null 或空白，否则无法进行字段加密</p>
     */
    private String sourceTableName;

    /**
     * 字段所属真实字段名（小写）
     * 
     * <p>这是数据库中实际存在的字段名，用于查找加密配置。
     * 如果字段名在配置中为 "phone"，则此字段应该为 "phone"（小写）。</p>
     * 
     * <p>不能为 null 或空白，否则无法进行字段加密</p>
     */
    private String sourceColumn;

    /**
     * 该字段是否直接从真实的表中关联获取的
     * 
     * <p>用于区分字段来源：</p>
     * <ul>
     *   <li>true: 字段直接来自真实表（如：SELECT user_name FROM tb_user）</li>
     *   <li>false: 字段来自子查询或派生表（如：SELECT a.userName FROM (SELECT user_name FROM tb_user) a）</li>
     * </ul>
     * 
     * <p>只有 fromSourceTable 为 true 的字段才会进行加密处理</p>
     * 
     * <p>示例：</p>
     * <pre>{@code
     * // 示例1：字段来自真实表
     * // SQL: SELECT user_name FROM tb_user
     * // fromSourceTable = true
     * 
     * // 示例2：字段来自子查询
     * // SQL: SELECT a.userName FROM (SELECT user_name FROM tb_user) a
     * // fromSourceTable = false（因为字段来自表 a，而表 a 不是真实表）
     * }</pre>
     */
    @Builder.Default
    private boolean fromSourceTable = false;

    /**
     * INSERT 字段索引位置（从1开始，对应 JDBC PreparedStatement 参数索引）
     * 
     * <p>用于 INSERT 语句中确定占位符对应的字段位置。</p>
     * 
     * <p>示例：</p>
     * <pre>{@code
     * // SQL: INSERT INTO user(id, name, phone) VALUES(?, ?, ?)
     * // id 的索引为 1（第一个占位符）
     * // name 的索引为 2（第二个占位符）
     * // phone 的索引为 3（第三个占位符）
     * }</pre>
     * 
     * <p>对于非 INSERT 语句（如 UPDATE、DELETE），此字段可以为 null</p>
     */
    private Integer insertFieldIndex;

    /**
     * 参数属性名（MyBatis 参数映射中的属性名）
     * 
     * <p>用于 MyBatis 场景，记录参数对象中的属性名。
     * 例如：如果参数是 User 对象，字段对应 user.name，则此字段为 "name"</p>
     * 
     * <p>可以为 null，表示非 MyBatis 场景或未配置</p>
     */
    private String parameterProperty;

    /**
     * 参数类型
     * 
     * <p>用于 MyBatis 场景，记录参数的类型。
     * 例如：如果参数类型是 String，则此字段为 "java.lang.String"</p>
     * 
     * <p>可以为 null，表示非 MyBatis 场景或未配置</p>
     */
    private String parameterType;
}
