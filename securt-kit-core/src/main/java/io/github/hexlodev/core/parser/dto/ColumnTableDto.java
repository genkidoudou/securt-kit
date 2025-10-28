package io.github.hexlodev.core.parser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表字段信息与占位符映射承载体
 * - tableAliasName: 当前层 FROM/JOIN 中的表别名
 * - sourceTableName: 真实表名（小写）
 * - sourceColumn: 真实列名（小写）
 * - fromSourceTable: 是否来自真实表（而非子查询/派生表）
 * - insertFieldIndex: INSERT 语句中字段所在的索引（从 0 开始），非 INSERT 场景可为 null
 * - parameterProperty/parameterType: MyBatis 参数映射属性与类型（可选）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnTableDto {
    /**
     * 字段所属的表的别名(from 后面接的表的别名)
     */
    private String tableAliasName;

    /**
     * 字段所属表的真实表的名字
     */
    private String sourceTableName;

    /**
     * 字段所属真实字段名
     */
    private String sourceColumn;

    /**
     * 该字段是否直接从真实的表中关联获取的
     * 栗子：select user_name   from tb_user    user_name 这个字段真实属于tb_user的，这个值就是ture
     * select a.userName from (select user_name   from tb_user )a    userName这个字段是来自于表a 的，表a不是真实的数据来源表，所以这个值是false
     **/
    @Builder.Default
    private boolean fromSourceTable = false;

    /**
     * INSERT 字段索引位置（从0开始）
     * 例如：INSERT INTO user(id, name, phone) VALUES(?, ?, ?)
     * id 的索引为 0，name 的索引为 1，phone 的索引为 2
     */
    private Integer insertFieldIndex;

    /**
     * 参数属性名（MyBatis 参数映射中的属性名）
     */
    private String parameterProperty;

    /**
     * 参数类型
     */
    private String parameterType;
}
