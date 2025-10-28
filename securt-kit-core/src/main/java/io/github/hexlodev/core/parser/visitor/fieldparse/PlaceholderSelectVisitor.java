package io.github.hexlodev.core.parser.visitor.fieldparse;

import io.github.hexlodev.core.parser.dto.ColumnTableDto;
import io.github.hexlodev.core.parser.dto.FieldInfoDto;
import io.github.hexlodev.core.parser.visitor.BaseFieldParseTable;
import io.github.hexlodev.core.parser.visitor.JsqlparserUtil;
import io.github.hexlodev.core.parser.visitor.PlaceholderExpressionVisitor;
import io.github.hexlodev.core.parser.visitor.PlaceholderFieldParseTable;
import io.github.hexlodev.core.utils.CollectionUtils;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.statement.select.*;

import java.util.*;

/**
 * 将 SELECT/VALUES 中的占位符与上游字段（INSERT 列或上层 SELECT 列）进行对应
 * - upstreamExpressionList: 当存在字段一一对应关系时，按索引对位
 * - visit(Values): 解析 VALUES，处理批量与单条两种形式
 */
public class PlaceholderSelectVisitor extends PlaceholderFieldParseTable implements SelectVisitor {

    /**
     * 需要和上游关联时的上游的表达式
     * 例如： ? in (select xxx from table) 这种场景，这里存储的就是上游的表达式
     **/
    private List<? extends Expression> upstreamExpressionList;


    private PlaceholderSelectVisitor(int layer,
                                     Map<String, Map<String, Set<FieldInfoDto>>> layerSelectTableFieldMap,
                                     Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap,
                                     Map<String, ColumnTableDto> placeholderColumnTableMap,
                                     List<? extends Expression> upstream) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap, placeholderColumnTableMap);
        this.upstreamExpressionList = Optional.ofNullable(upstream).orElse(new ArrayList<>());
    }

    /**
     * 返回当前层实例
     *
     * @author liutangqi
     * @date 2025/3/5 14:55
     * @Param [placeholderFieldParseTable]
     **/
    public static PlaceholderSelectVisitor newInstanceCurLayer(PlaceholderFieldParseTable placeholderFieldParseTable) {
        return PlaceholderSelectVisitor.newInstanceCurLayer(placeholderFieldParseTable, placeholderFieldParseTable.getPlaceholderColumnTableMap());
    }


    /**
     * 返回当前层实例
     *
     * @author liutangqi
     * @date 2025/3/5 14:55
     * @Param [placeholderFieldParseTable]
     **/
    public static PlaceholderSelectVisitor newInstanceCurLayer(PlaceholderFieldParseTable placeholderFieldParseTable,
                                                               List<? extends Expression> upstreamExpressionList) {
        return PlaceholderSelectVisitor.newInstanceCurLayer(placeholderFieldParseTable, placeholderFieldParseTable.getPlaceholderColumnTableMap(), upstreamExpressionList);
    }


    /**
     * 返回当前层实例
     *
     * @author liutangqi
     * @date 2025/3/5 14:55
     * @Param [placeholderFieldParseTable]
     **/
    public static PlaceholderSelectVisitor newInstanceCurLayer(BaseFieldParseTable fpt,
                                                               Map<String, ColumnTableDto> placeholderColumnTableMap,
                                                               List<? extends Expression> upstream) {
        return new PlaceholderSelectVisitor(
                fpt.getLayer(),
                fpt.getLayerSelectTableFieldMap(),
                fpt.getLayerFieldTableMap(),
                placeholderColumnTableMap,
                upstream
        );
    }

    /**
     * 返回当前层实例
     *
     * @author liutangqi
     * @date 2025/3/5 14:57
     * @Param [baseFieldParseTable, placeholderColumnTableMap]
     **/
    public static PlaceholderSelectVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable,
                                                               Map<String, ColumnTableDto> placeholderColumnTableMap) {
        return new PlaceholderSelectVisitor(baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap(),
                placeholderColumnTableMap,
                null);
    }

    /**
     * 返回当前层实例（INSERT语句专用）
     *
     * @author liutangqi
     * @date 2025/3/5 14:57
     * @Param [baseFieldParseTable, placeholderColumnTableMap, columns]
     **/
    public static PlaceholderSelectVisitor newInstanceCurLayerForInsert(BaseFieldParseTable baseFieldParseTable,
                                                                        Map<String, ColumnTableDto> placeholderColumnTableMap,
                                                                        List<net.sf.jsqlparser.schema.Column> columns) {
        // 将columns转换为Expression列表
        List<Expression> upstreamExpressions = new ArrayList<>();
        if (columns != null) {
            for (net.sf.jsqlparser.schema.Column column : columns) {
                upstreamExpressions.add(column);
            }
        }
        
        return new PlaceholderSelectVisitor(baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap(),
                placeholderColumnTableMap,
                upstreamExpressions);
    }

    /**
     * 返回下一层实例
     *
     * @author liutangqi
     * @date 2025/3/5 14:55
     * @Param [placeholderFieldParseTable]
     **/
    public static PlaceholderSelectVisitor newInstanceNextLayer(PlaceholderFieldParseTable placeholderFieldParseTable) {
        return new PlaceholderSelectVisitor((placeholderFieldParseTable.getLayer() + 1),
                placeholderFieldParseTable.getLayerSelectTableFieldMap(),
                placeholderFieldParseTable.getLayerFieldTableMap(),
                placeholderFieldParseTable.getPlaceholderColumnTableMap(),
                null);
    }

    /**
     * 场景1：
     * select
     * (select xx from tb)as xxx -- 这种语法
     * from tb
     * 场景2：
     * xxx in (select xxx from) -- 括号里面的这种语法
     *
     * @author liutangqi
     * @date 2025/3/17 14:10
     * @Param [parenthesedSelect]
     **/
    @Override
    public void visit(ParenthesedSelect subSelect) {
        //处理子查询内容（注意：这里层数是当前层，这个的解析结果需要和外层在同一层级）
        Optional.ofNullable(subSelect.getSelect())
                .ifPresent(p -> p.accept(PlaceholderSelectVisitor.newInstanceCurLayer(this, this.upstreamExpressionList)));
    }

    @Override
    public void visit(PlainSelect plainSelect) {
        //1.获取select的每一项，将其中 select (select a from xxx) from 这种语法的#{}占位符进行解析
        PlaceholderExpressionVisitor placeholderWhereExpressionVisitor = PlaceholderExpressionVisitor.newInstanceCurLayer(this);
        //1.1 上游字段和当前查询字段有对应关系的时候，查的字段的数量一定是相同的
        if (this.upstreamExpressionList.size() == plainSelect.getSelectItems().size()) {
            for (int i = 0; i < plainSelect.getSelectItems().size(); i++) {
                PlaceholderExpressionVisitor placeholderExpressionVisitor = PlaceholderExpressionVisitor.newInstanceCurLayer(this,
                        this.upstreamExpressionList.get(i));
                plainSelect.getSelectItems().get(i).getExpression().accept(placeholderExpressionVisitor);
            }
        }
        //1.2 上游字段和当前没对应关系时，使用同一个visitor即可
        else {
            plainSelect.getSelectItems()
                    .stream()
                    .forEach(f -> f.getExpression().accept(placeholderWhereExpressionVisitor));
        }


        //2.解析from 后面的 #{}占位符
        FromItem fromItem = plainSelect.getFromItem();
        if (fromItem != null) {
            PlaceholderSelectFromItemVisitor placeholderSelectFromItemVisitor = PlaceholderSelectFromItemVisitor.newInstanceCurLayer(this);
            fromItem.accept(placeholderSelectFromItemVisitor);
        }

        //3.将where 条件中的#{} 占位符进行解析
        Expression where = plainSelect.getWhere();
        if (where != null) {
            where.accept(placeholderWhereExpressionVisitor);
        }

        //4.解析join
        List<Join> joins = plainSelect.getJoins();
        if (CollectionUtils.isNotEmpty(joins)) {
            PlaceholderSelectFromItemVisitor phFromItemVisitor = PlaceholderSelectFromItemVisitor.newInstanceCurLayer(this);
            for (Join join : joins) {
                //4.1解析join的表
                FromItem joinRightItem = join.getRightItem();
                joinRightItem.accept(phFromItemVisitor);
                //4.2解析on
                for (Expression expression : join.getOnExpressions()) {
                    expression.accept(PlaceholderExpressionVisitor.newInstanceCurLayer(this));
                }
            }
        }
    }

    /**
     * union  ，union all 语法，将每个sql分开解析，获取其中的#{}占位符
     *
     * @author liutangqi
     * @date 2024/7/17 17:26
     * @Param [setOperationList]
     **/
    @Override
    public void visit(SetOperationList setOperationList) {
        List<Select> selects = setOperationList.getSelects();
        for (Select select : selects) {
            //单独解析这条sql
            FieldParseParseTableSelectVisitor fieldParseParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
            select.accept(fieldParseParseTableSelectVisitor);

            //用解析后的结果，去解析#{}占位符  (字段所属信息从上面解析结果中取，存放占位符的解析结果的Map用当前的 )
            PlaceholderSelectVisitor placeholderSelectVisitor = PlaceholderSelectVisitor.newInstanceCurLayer(fieldParseParseTableSelectVisitor, this.getPlaceholderColumnTableMap());
            select.accept(placeholderSelectVisitor);

        }
    }

    @Override
    public void visit(WithItem withItem) {

    }

    /**
     * 处理 VALUES：
     * - 批量插入：遍历每个 ExpressionList
     * - 单条插入：按照 i 索引将 INSERT 字段与右侧值一一对应
     */
    @Override
    public void visit(Values aThis) {
        log.info("【securt-kit】开始处理Values: {}", aThis.toString());
        log.info("【securt-kit】upstreamExpressionList大小: {}", upstreamExpressionList.size());
        
        ExpressionList<Expression> expressions = (ExpressionList<Expression>) aThis.getExpressions();
        log.info("【securt-kit】expressions大小: {}", expressions.size());
        
        // 检查是否是批量插入（多个VALUES子句）
        boolean isBatchInsert = expressions.size() > 1 && expressions.get(0) instanceof ExpressionList;
        
        if (isBatchInsert) {
            log.info("【securt-kit】处理批量Values，VALUES子句数量: {}", expressions.size());
            // 处理批量插入：每个VALUES子句都需要处理
            for (int valuesIndex = 0; valuesIndex < expressions.size(); valuesIndex++) {
                Expression expressionList = expressions.get(valuesIndex);
                if (expressionList instanceof ExpressionList) {
                    ExpressionList eList = (ExpressionList) expressionList;
                    log.info("【securt-kit】处理第{}个ExpressionList，大小: {}", valuesIndex + 1, eList.size());
                    for (int i = 0; i < eList.size(); i++) {
                        Expression curExp = (Expression) eList.get(i);
                        Expression upstreamExpression = this.upstreamExpressionList.get(i);
                        // 使用标准的占位符格式，不添加批次索引
                        log.info("【securt-kit】处理表达式对 {}: upstream={}, current={}", 
                                i, upstreamExpression, curExp);
                        JsqlparserUtil.parseWhereColumTable(this.getLayer(),
                                this.getLayerFieldTableMap(),
                                upstreamExpression,
                                curExp,
                                this.getPlaceholderColumnTableMap(),
                                i); // 传递 INSERT 字段索引
                    }
                }
            }
        } else {
            // 处理单个VALUES子句
            if (expressions.get(0) instanceof ExpressionList) {
                ExpressionList eList = (ExpressionList) expressions.get(0);
                log.info("【securt-kit】处理单个ExpressionList，大小: {}", eList.size());
                for (int i = 0; i < eList.size(); i++) {
                    Expression curExp = (Expression) eList.get(i);
                    Expression upstreamExpression = this.upstreamExpressionList.get(i);
                    log.info("【securt-kit】处理表达式对 {}: upstream={}, current={}", i, upstreamExpression, curExp);
                    JsqlparserUtil.parseWhereColumTable(this.getLayer(),
                            this.getLayerFieldTableMap(),
                            upstreamExpression,
                            curExp,
                            this.getPlaceholderColumnTableMap(),
                            i); // 传递 INSERT 字段索引
                }
            } else {
                log.info("【securt-kit】处理单个Values");
                for (int i = 0; i < expressions.size(); i++) {
                    Expression curExp = (Expression) expressions.get(i);
                    Expression upstreamExpression = this.upstreamExpressionList.get(i);
                    log.info("【securt-kit】处理表达式对 {}: upstream={}, current={}", i, upstreamExpression, curExp);
                    JsqlparserUtil.parseWhereColumTable(this.getLayer(),
                            this.getLayerFieldTableMap(),
                            upstreamExpression,
                            curExp,
                            this.getPlaceholderColumnTableMap(),
                            i); // 传递 INSERT 字段索引
                }
            }
        }
        
        log.info("【securt-kit】Values处理完成，placeholderColumnTableMap大小: {}", this.getPlaceholderColumnTableMap().size());
    }

    @Override
    public void visit(LateralSubSelect lateralSubSelect) {

    }

    @Override
    public void visit(TableStatement tableStatement) {

    }

}
