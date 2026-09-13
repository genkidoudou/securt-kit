package io.github.genkidoudou.core.parser.visitor.fieldparse;

import io.github.genkidoudou.core.parser.dto.ColumnTableDto;
import io.github.genkidoudou.core.parser.visitor.BaseFieldParseTable;
import io.github.genkidoudou.core.parser.visitor.PlaceholderFieldParseTable;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;

import java.util.Map;

/**
 * 解析 FROM 子查询中的占位符：
 * - 对 (SELECT ...) 作为 FromItem 的情况，进入下一层 PlaceholderSelectVisitor 继续解析
 */
public class PlaceholderSelectFromItemVisitor extends PlaceholderFieldParseTable implements FromItemVisitor {

    private PlaceholderSelectFromItemVisitor(BaseFieldParseTable baseFieldParseTable, Map<String, ColumnTableDto> placeholderColumnTableMap) {
        super(baseFieldParseTable, placeholderColumnTableMap);
    }

    public static PlaceholderSelectFromItemVisitor newInstanceCurLayer(PlaceholderFieldParseTable placeholderFieldParseTable) {
        return new PlaceholderSelectFromItemVisitor(placeholderFieldParseTable, placeholderFieldParseTable.getPlaceholderColumnTableMap());
    }

    @Override
    public void visit(Table table) {
    }


    /**
     * 子查询作为 FromItem：占位符解析下沉到下一层 select visitor
     */
    @Override
    public void visit(ParenthesedSelect subSelect) {
        PlaceholderSelectVisitor placeholderSelectVisitor = PlaceholderSelectVisitor.newInstanceNextLayer(this);
        subSelect.getSelect().accept(placeholderSelectVisitor);
    }


    @Override
    public void visit(LateralSubSelect lateralSubSelect) {

    }


    @Override
    public void visit(TableFunction tableFunction) {

    }

    @Override
    public void visit(ParenthesedFromItem aThis) {

    }
}
