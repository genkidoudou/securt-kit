package io.github.genkidoudou.core.parser.visitor.fieldparse;

import io.github.genkidoudou.core.parser.dto.FieldInfoDto;
import io.github.genkidoudou.core.parser.visitor.BaseFieldParseTable;
import net.sf.jsqlparser.statement.select.*;

import java.util.*;

/**
 * 解析 select 语句：
 * - FROM/JOIN：使用 FieldParseParseTableFromItemVisitor 解析表字段全集
 * - SELECT items：使用 FieldParseParseSelectItemVisitor 解析每一项字段并写入 layerSelectTableFieldMap
 */
public class FieldParseParseTableSelectVisitor extends BaseFieldParseTable implements SelectVisitor {
    private FieldParseParseTableSelectVisitor(int layer,
                                              Map<String, Map<String, Set<FieldInfoDto>>> layerSelectTableFieldMap,
                                              Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
    }

    public static FieldParseParseTableSelectVisitor newInstanceFirstLayer() {
        return new FieldParseParseTableSelectVisitor(1, new HashMap<>(), new HashMap<>());
    }

    public static FieldParseParseTableSelectVisitor newInstanceFirstLayer(Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap) {
        return new FieldParseParseTableSelectVisitor(1, new HashMap<>(), layerFieldTableMap);
    }

    public static FieldParseParseTableSelectVisitor newInstanceNextLayer(BaseFieldParseTable baseFieldParseTable) {
        return new FieldParseParseTableSelectVisitor(
                baseFieldParseTable.getLayer() + 1,
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap()
        );
    }

    @Override
    public void visit(ParenthesedSelect parenthesedSelect) {

    }

    /**
     * 解析 PlainSelect：FROM、JOIN、SELECT 列
     */
    @Override
    public void visit(PlainSelect plainSelect) {
        // from 的表
        FromItem fromItem = plainSelect.getFromItem();
        if (fromItem != null) {
            FieldParseParseTableFromItemVisitor fieldParseTableFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceCurLayer(this);
            fromItem.accept(fieldParseTableFromItemVisitor);
        }
        //join 的表
        List<Join> joins = Optional.ofNullable(plainSelect.getJoins()).orElse(new ArrayList<>());
        for (Join join : joins) {
            FromItem rightItem = join.getRightItem();
            FieldParseParseTableFromItemVisitor joinFieldTableFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceCurLayer(this);
            rightItem.accept(joinFieldTableFromItemVisitor);
        }
        //查询的全部字段
        List<SelectItem<?>> selectItems = plainSelect.getSelectItems();
        for (int i = 0; i < selectItems.size(); i++) {
            SelectItem selectItem = selectItems.get(i);
            FieldParseParseSelectItemVisitor fieldParseSelectItemVisitor =
                    FieldParseParseSelectItemVisitor.newInstanceCurLayer(this, i + 1);
            selectItem.accept(fieldParseSelectItemVisitor);
        }
    }

    @Override
    public void visit(SetOperationList setOperationList) {

    }

    @Override
    public void visit(WithItem withItem) {

    }

    @Override
    public void visit(Values values) {

    }

    @Override
    public void visit(LateralSubSelect lateralSubSelect) {

    }

    @Override
    public void visit(TableStatement tableStatement) {

    }
}
