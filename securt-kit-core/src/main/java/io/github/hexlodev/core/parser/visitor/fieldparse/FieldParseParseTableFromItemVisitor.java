package io.github.hexlodev.core.parser.visitor.fieldparse;

import cn.hutool.core.collection.CollectionUtil;
import io.github.hexlodev.core.TableCache;
import io.github.hexlodev.core.parser.dto.FieldInfoDto;
import io.github.hexlodev.core.parser.visitor.BaseFieldParseTable;
import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;

import java.util.*;

/**
 * 解析 FROM/JOIN 项：
 * - 将当前层可用的表字段全集放入 layerFieldTableMap（表别名 → 字段集合）
 * - 字段来源于 TableCache（已按注解/配置合并），用于后续占位符匹配
 */
public class FieldParseParseTableFromItemVisitor extends BaseFieldParseTable implements FromItemVisitor {
    private FieldParseParseTableFromItemVisitor(int layer, Map<String, Map<String, Set<FieldInfoDto>>> layerSelectTableFieldMap, Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
    }

    public static FieldParseParseTableFromItemVisitor newInstanceFirstLayer() {
        return new FieldParseParseTableFromItemVisitor(1, new HashMap<>(), new HashMap<>());
    }

    public static FieldParseParseTableFromItemVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable) {
        return new FieldParseParseTableFromItemVisitor(
                baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap()
        );
    }

    @Override
    public void visit(Table table) {
        //解析表结构信息
        String tableName = table.getName();
        String tableAlias = Optional.ofNullable(table.getAlias()).map(Alias::getName).orElse(tableName);
        // 从 TableCache 中获取表的字段信息
        Set<FieldInfoDto> fieldInfoDtos = new HashSet<>();
        if (TableCache.concatTable(tableName)) {
            Map<String, Class<? extends FieldEncryptorStrategy>> tableFieldEncryptInfo =
                    TableCache.getTableFieldEncryptInfo(tableName);
            if (CollectionUtil.isNotEmpty(tableFieldEncryptInfo)) {
                for (Map.Entry<String, Class<? extends FieldEncryptorStrategy>> entry : tableFieldEncryptInfo.entrySet()) {
                    String columnName = entry.getKey();
                    Class<? extends FieldEncryptorStrategy> value = entry.getValue();
                    FieldInfoDto fieldInfo = new FieldInfoDto(
                            columnName,  // columnName
                            columnName,  // sourceColumn
                            tableName.toLowerCase(),  // sourceTableName
                            true  // fromSourceTable
                    );
                    fieldInfoDtos.add(fieldInfo);
                }
            }
        }

        // 将表字段信息存储到 layerFieldTableMap 中
        Map<String, Set<FieldInfoDto>> tableFieldMap = this.getLayerFieldTableMap().computeIfAbsent(
                String.valueOf(this.getLayer()), k -> new HashMap<>()
        );
        tableFieldMap.put(tableAlias.toLowerCase(), fieldInfoDtos);
    }

    @Override
    public void visit(ParenthesedSelect parenthesedSelect) {

    }

    @Override
    public void visit(LateralSubSelect lateralSubSelect) {

    }

    @Override
    public void visit(TableFunction tableFunction) {

    }

    @Override
    public void visit(ParenthesedFromItem parenthesedFromItem) {

    }
}
