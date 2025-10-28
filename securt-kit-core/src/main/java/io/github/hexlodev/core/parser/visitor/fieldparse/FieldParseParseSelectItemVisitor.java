package io.github.hexlodev.core.parser.visitor.fieldparse;

import io.github.hexlodev.core.parser.dto.FieldInfoDto;
import io.github.hexlodev.core.parser.visitor.BaseFieldParseTable;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SelectItemVisitor;

import java.util.Map;
import java.util.Set;

/**
 * 维护当前层 SELECT 子句中的每个查询项
 * - 为每个 SelectItem 创建 FieldParseParseExpressionVisitor 解析其表达式（含别名）
 */
public class FieldParseParseSelectItemVisitor extends BaseFieldParseTable implements SelectItemVisitor {

    /**
     * 获取当前层实例对象
     *
     * @author liutangqi
     * @date 2025/3/4 17:25
     * @Param [baseFieldParseTable]
     **/
    public static FieldParseParseSelectItemVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable) {
        return new FieldParseParseSelectItemVisitor(
                baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap()
        );
    }

    private FieldParseParseSelectItemVisitor(int layer, Map<String, Map<String, Set<FieldInfoDto>>> layerSelectTableFieldMap, Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
    }

    /**
     * 处理一个 SelectItem：下发到表达式 visitor 解析
     */
    @Override
    public void visit(SelectItem selectItem) {
        Expression expression = selectItem.getExpression();

        Alias alias = selectItem.getAlias();
        FieldParseParseExpressionVisitor fieldParseExpressionVisitor = FieldParseParseExpressionVisitor.newInstanceCurLayer(this, alias);
        expression.accept(fieldParseExpressionVisitor);

    }

}
