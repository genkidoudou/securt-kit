package io.github.hexlodev.core.parser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段信息 DTO（用于 SELECT 层字段上下文）
 * - columnName：SELECT 中显示的列名或别名
 * - sourceColumn/sourceTableName：真实列与表
 * - fromSourceTable：是否直接来源于物理表（而非派生/子查询）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldInfoDto {
    
    /**
     * 列名（查询结果中的字段名或别名）
     */
    private String columnName;
    
    /**
     * 源列名（实际数据库表中的字段名）
     */
    private String sourceColumn;
    
    /**
     * 源表名
     */
    private String sourceTableName;
    
    /**
     * 该字段是否直接从真实的表中关联获取的
     * 栗子：select user_name   from tb_user    user_name 这个字段真实属于tb_user的，这个值就是ture
     * select a.userName from (select user_name   from tb_user )a    userName这个字段是来自于表a 的，表a不是真实的数据来源表，所以这个值是false
     */
    @Builder.Default
    private boolean fromSourceTable = false;
}