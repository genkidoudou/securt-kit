package io.github.hexlodev.core.parser.visitor;

import io.github.hexlodev.core.parser.dto.FieldInfoDto;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 解析 SQL 字段信息的基类：维护按“层（layer）”划分的字段上下文
 * - layerSelectTableFieldMap：第 layer 层 SELECT 中出现的字段（列/别名 → 源表/源列）
 * - layerFieldTableMap：第 layer 层 FROM/JOIN 中可用的全部字段（表别名 → 源表/源列全集）
 * 说明：当存在子查询/派生表时，同层的字段不一定属于同一张物理表
 */
@Slf4j
@Getter
public class BaseFieldParseTable {
    /**
     * 层数
     */
    private int layer;
    /**
     * 存储sql中 第layer 层select 中出现字段
     * key： layer层数
     * value:
     * ---key: 表别名(有表别名就是别名，没有别名就是表名)
     * --- value: (注意：这里嵌套子查询时，这里的所有字段不一定属于同一张表)
     * -------出现的字段的 "别名" 和这个字段所属的真实表名
     **/
    private Map<String, Map<String, Set<FieldInfoDto>>> layerSelectTableFieldMap;
    /**
     * 存储sql中 第layer 层查询表 拥有的全部字段
     * key： layer层数
     * value:
     * ---key: 表别名(有表别名就是别名，没有别名就是表名)
     * --- value: (注意：这里嵌套子查询时，这里的所有字段不一定属于同一张表)
     * ------查询的表拥有的全部 "字段原名"和所属的真实表名
     */
    private Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap;

    public BaseFieldParseTable(int layer,
                               Map<String, Map<String, Set<FieldInfoDto>>> layerSelectTableFieldMap,
                               Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap) {
        this.layer = layer;
        this.layerSelectTableFieldMap = Optional.ofNullable(layerSelectTableFieldMap).orElse(new HashMap<>());
        this.layerFieldTableMap = Optional.ofNullable(layerFieldTableMap).orElse(new HashMap<>());
    }

}
