package io.github.genkidoudou.core.parser.visitor;

import io.github.genkidoudou.core.parser.constant.FieldConstant;
import io.github.genkidoudou.core.parser.dto.ColumnTableDto;
import io.github.genkidoudou.core.parser.dto.ParameterMatchType;
import io.github.genkidoudou.core.parser.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import io.github.genkidoudou.core.parser.visitor.fieldparse.PlaceholderSelectVisitor;
import cn.hutool.core.collection.CollectionUtil;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.arithmetic.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.conditional.XorExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.Select;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 解析 WHERE/CASE/IN 等表达式中出现的占位符
 * - 当一侧为占位符，另一侧为列时，建立占位符→列(ColumnTableDto)映射
 * - 支持复杂语法：AND/OR、IN（多列）、子查询、CASE WHEN 等
 */
public class PlaceholderExpressionVisitor extends PlaceholderFieldParseTable implements ExpressionVisitor {

    /**
     * 当此表达式和上游表达式相关联时，这个是传的上游的表达式
     * 例如：  case 字段 when xxx    的when语句的时候，需要知道这个when语句所属的case后面的字段，这个变量是存储这种情况的case字段的
     */
    private Expression upstreamExpression;

    /**
     * 获取当前层解析对象
     *
     * @author liutangqi
     * @date 2025/3/5 10:40
     * @Param [placeholderFieldParseTable]
     **/
    public static PlaceholderExpressionVisitor newInstanceCurLayer(PlaceholderFieldParseTable placeholderFieldParseTable, Expression upstreamExpression) {
        return new PlaceholderExpressionVisitor(placeholderFieldParseTable, placeholderFieldParseTable.getPlaceholderColumnTableMap(), upstreamExpression);
    }

    /**
     * 获取当前层解析对象
     *
     * @author liutangqi
     * @date 2025/3/5 10:40
     * @Param [placeholderFieldParseTable]
     **/
    public static PlaceholderExpressionVisitor newInstanceCurLayer(PlaceholderFieldParseTable placeholderFieldParseTable) {
        return new PlaceholderExpressionVisitor(placeholderFieldParseTable, placeholderFieldParseTable.getPlaceholderColumnTableMap(), null);
    }

    /**
     * 获取当前层解析对象
     *
     * @author liutangqi
     * @date 2025/3/5 10:40
     * @Param [placeholderFieldParseTable]
     **/
    public static PlaceholderExpressionVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable, Map<String, ColumnTableDto> placeholderColumnTableMap) {
        return new PlaceholderExpressionVisitor(baseFieldParseTable, placeholderColumnTableMap, null);
    }

    private PlaceholderExpressionVisitor(BaseFieldParseTable baseFieldParseTable, Map<String, ColumnTableDto> placeholderColumnTableMap, Expression upstreamExpression) {
        super(baseFieldParseTable, placeholderColumnTableMap);
        this.upstreamExpression = upstreamExpression;
    }

    public Expression getUpstreamExpression() {
        return upstreamExpression;
    }
    @Override
    public void visit(BitwiseRightShift aThis) {

    }

    @Override
    public void visit(BitwiseLeftShift aThis) {

    }

    @Override
    public void visit(NullValue nullValue) {

    }

    /**
     * 函数：函数本身不产生映射，但参数里可能藏着列或占位符，需要继续往下解析
     * 栗子： date_format(create_time,?) 、 concat(name,?) = ?
     */
    @Override
    public void visit(Function function) {
        ExpressionList<?> parameters = function.getParameters();
        if (parameters == null) {
            return;
        }
        for (Object parameter : parameters) {
            if (parameter instanceof Expression) {
                ((Expression) parameter).accept(this);
            }
        }
    }

    @Override
    public void visit(SignedExpression signedExpression) {

    }

    @Override
    public void visit(JdbcParameter jdbcParameter) {

    }

    @Override
    public void visit(JdbcNamedParameter jdbcNamedParameter) {

    }

    @Override
    public void visit(DoubleValue doubleValue) {

    }

    @Override
    public void visit(LongValue longValue) {

    }

    @Override
    public void visit(HexValue hexValue) {

    }

    @Override
    public void visit(DateValue dateValue) {

    }

    @Override
    public void visit(TimeValue timeValue) {

    }

    @Override
    public void visit(TimestampValue timestampValue) {

    }

    /**
     * 括起来的一堆条件
     * @author luyanan
     * @since 2025/10/10
     */
    @Override
    public void visit(Parenthesis parenthesis) {
        //解析括号括起来的表达式
        Expression exp = parenthesis.getExpression();
        exp.accept(this);
    }

    @Override
    public void visit(StringValue stringValue) {

    }

    @Override
    public void visit(Addition addition) {

    }

    @Override
    public void visit(Division division) {

    }

    @Override
    public void visit(IntegerDivision division) {

    }

    @Override
    public void visit(Multiplication multiplication) {

    }

    @Override
    public void visit(Subtraction subtraction) {

    }

    @Override
    public void visit(AndExpression andExpression) {
        //pojo模式处理左右表达式
        JsqlparserUtil.visitPojoBinaryExpression(this, andExpression);
    }

    @Override
    public void visit(OrExpression orExpression) {
        //pojo模式处理左右表达式
        JsqlparserUtil.visitPojoBinaryExpression(this, orExpression);
    }

    @Override
    public void visit(XorExpression orExpression) {
        //pojo模式处理左右表达式
        JsqlparserUtil.visitPojoBinaryExpression(this, orExpression);
    }

    /**
     * BETWEEN：左边是列时，起止两个表达式都可能是占位符，需要分别建立映射
     * 栗子： where phone between ? and ?
     */
    @Override
    public void visit(Between between) {
        Expression leftExpression = between.getLeftExpression();

        JsqlparserUtil.parseWhereColumTable(this.getLayer(),
                this.getLayerFieldTableMap(),
                leftExpression,
                between.getBetweenExpressionStart(),
                this.getPlaceholderColumnTableMap());

        JsqlparserUtil.parseWhereColumTable(this.getLayer(),
                this.getLayerFieldTableMap(),
                leftExpression,
                between.getBetweenExpressionEnd(),
                this.getPlaceholderColumnTableMap());
    }

    @Override
    public void visit(OverlapsCondition overlapsCondition) {

    }

    /**
     * 等值：若一侧为占位符一侧为列，则记录映射
     */
    @Override
    public void visit(EqualsTo equalsTo) {
        //如果有一边表达式是 特殊的占位符，则维护占位符对应的表字段信息
        JsqlparserUtil.parseWhereColumTable(this.getLayer(),
                this.getLayerFieldTableMap(),
                equalsTo,
                this.getPlaceholderColumnTableMap());
    }

    @Override
    public void visit(GreaterThan greaterThan) {
        //如果有一边表达式是 特殊的占位符，则维护占位符对应的表字段信息
        JsqlparserUtil.parseWhereColumTable(this.getLayer(),
                this.getLayerFieldTableMap(),
                greaterThan,
                this.getPlaceholderColumnTableMap());
    }

    @Override
    public void visit(GreaterThanEquals greaterThanEquals) {
        //如果有一边表达式是 特殊的占位符，则维护占位符对应的表字段信息
        JsqlparserUtil.parseWhereColumTable(this.getLayer(),
                this.getLayerFieldTableMap(),
                greaterThanEquals,
                this.getPlaceholderColumnTableMap());
    }
    /**
     * IN：处理 (列,列) IN ((?,?),(?,?)) 等复杂组合
     */
    @Override
    public void visit(InExpression inExpression) {
        Expression leftExpression = inExpression.getLeftExpression();
        Expression rightExpression = inExpression.getRightExpression();
        List<Expression> leftExpressionList = new ArrayList<>();

        //1.记录左边的表达式,用于后面右边和左边对应时，解析占位符
        //1.1 左边是单列的常量或者是字段列时（对应语法1，语法2，语法3）
        if ((leftExpression instanceof Column) || (inExpression.getLeftExpression() instanceof JdbcParameter)) {
            leftExpressionList.add(leftExpression);
        }
        //1.2 左边是多值字段时（对应语法4，语法5）
        else if (leftExpression instanceof ParenthesedExpressionList) {
            ParenthesedExpressionList<Expression> leftParenthesedExpressionList = (ParenthesedExpressionList<Expression>) leftExpression;
            for (Expression expression : leftParenthesedExpressionList) {
                leftExpressionList.add(expression);
            }
        }
        //1.3 左边是其它情况时（对应语法6）
        else {
            //这种情况不做处理，这种情况#{}占位符所属的字段信息是一个聚合结果，同时来源多张表，不支持此种写法，两个单独的字段聚合后，单独加密和整体加密密文肯定不同
            // 写出这种sql的时候请反省一下自己，表结构是不是有问题，硬要用这种写法的，请使用数据库函数加密的db模式
        }

        //2.解析右边的表达式，和左边做对应，解析对应的占位符信息
        //2.1 当右边是多列，并且右边也是多列的集合时（对应语法4）
        if ((rightExpression instanceof ParenthesedExpressionList) && (((ParenthesedExpressionList) rightExpression).get(0)) instanceof ExpressionList) {
            ParenthesedExpressionList<ExpressionList> rightExpressionList = (ParenthesedExpressionList<ExpressionList>) rightExpression;
            for (ExpressionList<Expression> expList : rightExpressionList) {
                for (int i = 0; i < expList.size(); i++) {
                    //找出对应的左边的表达式
                    Expression leftExp = leftExpressionList.get(i);
                    //解析占位符
                    JsqlparserUtil.parseWhereColumTable(this.getLayer(),
                            this.getLayerFieldTableMap(),
                            leftExp,
                            expList.get(i),
                            this.getPlaceholderColumnTableMap());
                }
            }
        }
        //2.2 当右边是多列的其它情况（对应语法1,语法6）注意：此时左边肯定只有1列，所以左边如果存在密文存储的字段，则右边全部都需要处理
        else if ((rightExpression instanceof ParenthesedExpressionList)) {
            ParenthesedExpressionList<Expression> rightExpressionList = (ParenthesedExpressionList<Expression>) rightExpression;
            for (int i = 0; i < rightExpressionList.size(); i++) {
                //找出对应的左边的表达式（语法1中左表达式集合长度肯定为1，所以get(0)，语法6这种不兼容，所以直接返回null）
                Expression leftExp = CollectionUtil.isNotEmpty(leftExpressionList) ? leftExpressionList.get(0) : null;
                //解析占位符
                JsqlparserUtil.parseWhereColumTable(this.getLayer(),
                        this.getLayerFieldTableMap(),
                        leftExp,
                        rightExpressionList.get(i),
                        this.getPlaceholderColumnTableMap());
            }
        }
        //2.3 当右边是子查询时 （对应语法2，语法3，语法5 ）
        else if (rightExpression instanceof ParenthesedSelect) {
            ParenthesedSelect rightSelect = (ParenthesedSelect) rightExpression;
            //这种情况右边是一个完全独立的sql，单独解析
            FieldParseParseTableSelectVisitor fPTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
            rightSelect.accept(fPTableSelectVisitor);
            //用这个解析的结果集解析where后面的占位符
            PlaceholderSelectVisitor placeholderSelectVisitor = PlaceholderSelectVisitor.newInstanceCurLayer(fPTableSelectVisitor,
                    this.getPlaceholderColumnTableMap(),
                    leftExpressionList);
            rightSelect.accept(placeholderSelectVisitor);
        }
    }

    @Override
    public void visit(FullTextSearch fullTextSearch) {

    }

    @Override
    public void visit(IsNullExpression isNullExpression) {

    }

    @Override
    public void visit(IsBooleanExpression isBooleanExpression) {

    }

    @Override
    public void visit(LikeExpression likeExpression) {
        // LIKE：建立占位符映射，并标记为 LIKE，加密前走 LikePatternHandler
        JsqlparserUtil.parseWhereColumTable(this.getLayer(),
                this.getLayerFieldTableMap(),
                likeExpression,
                this.getPlaceholderColumnTableMap(),
                ParameterMatchType.LIKE);
    }

    @Override
    public void visit(MinorThan minorThan) {
        //如果有一边表达式是 特殊的占位符，则维护占位符对应的表字段信息
        JsqlparserUtil.parseWhereColumTable(this.getLayer(),
                this.getLayerFieldTableMap(),
                minorThan,
                this.getPlaceholderColumnTableMap());
    }

    @Override
    public void visit(MinorThanEquals minorThanEquals) {
        //如果有一边表达式是 特殊的占位符，则维护占位符对应的表字段信息
        JsqlparserUtil.parseWhereColumTable(this.getLayer(),
                this.getLayerFieldTableMap(),
                minorThanEquals,
                this.getPlaceholderColumnTableMap());
    }

    @Override
    public void visit(NotEqualsTo notEqualsTo) {
        //如果有一边表达式是 特殊的占位符，则维护占位符对应的表字段信息
        JsqlparserUtil.parseWhereColumTable(this.getLayer(),
                this.getLayerFieldTableMap(),
                notEqualsTo,
                this.getPlaceholderColumnTableMap());
    }

    @Override
    public void visit(DoubleAnd doubleAnd) {

    }

    @Override
    public void visit(Contains contains) {

    }

    @Override
    public void visit(ContainedBy containedBy) {

    }

    @Override
    public void visit(ParenthesedSelect selectBody) {

    }

    @Override
    public void visit(Column tableColumn) {
        //当上游字段不为空时，说明这个列和上游字段是相互对应的，所以处理他们的占位符对应关系
        if (this.upstreamExpression != null) {
            JsqlparserUtil.parseWhereColumTable(this.getLayer(),
                    this.getLayerFieldTableMap(),
                    this.upstreamExpression,
                    tableColumn,
                    this.getPlaceholderColumnTableMap());
        }
    }

    /**
     * case 字段 when xxx then
     * case when 字段=xxx then
     * 只有下面情况的占位符才有字段对应，需要进行处理
     * 情况1: case 表字段  when ?占位符 then xxx
     * 情况2: case when 表字段=? then （这种情况条件在when里面）
     * 情况3：... then 表字段>= ?占位符
     * @author luyanan
     * @since 2025/10/10
     */
    @Override
    public void visit(CaseExpression caseExpression) {
        //记录当前的case 后面的所属字段，如果when 语句后面是表达式的话，需要知道case后面的所属字段，才知道是否需要加密
        Expression upstreamExpression = caseExpression.getSwitchExpression();

        //处理when条件
        if (CollectionUtil.isNotEmpty(caseExpression.getWhenClauses())) {
            for (WhenClause whenClause : caseExpression.getWhenClauses()) {
                //这里处理的逻辑在下面的public void visit(WhenClause whenClause)会处理
                PlaceholderExpressionVisitor placeholderWhereExpressionVisitor = PlaceholderExpressionVisitor.newInstanceCurLayer(this, upstreamExpression);
                whenClause.accept(placeholderWhereExpressionVisitor);
            }
        }

        //else条件 只用处理else中是 表达式的场景  栗子：  case 字段 when xxx then  字段 >= ?占位符  else 字段 >= ?占位符，只有这种情况下，占位符才有对应的表字段信息
        Expression elseExpression = caseExpression.getElseExpression();
        if (elseExpression instanceof BinaryExpression) {
            //如果有一边表达式是 特殊的占位符，则维护占位符对应的表字段信息
            JsqlparserUtil.parseWhereColumTable(this.getLayer(),
                    this.getLayerFieldTableMap(),
                    (BinaryExpression) elseExpression,
                    this.getPlaceholderColumnTableMap());
        }

    }

    /**
     *      *

     * @author luyanan
     * @since 2025/10/10
     */
    @Override
    public void visit(WhenClause whenClause) {
        //对应case when的情况1： case 表字段  when ?占位符 then xxx
        if (this.upstreamExpression instanceof Column && whenClause.getWhenExpression().toString().contains(FieldConstant.PLACEHOLDER)) {
            ColumnTableDto columnTableDto = JsqlparserUtil.parseColumn((Column) this.upstreamExpression, this.getLayer(), this.getLayerFieldTableMap());
            this.getPlaceholderColumnTableMap().put(whenClause.getWhenExpression().toString(), columnTableDto);
        }

        //对应case when 的情况2： case  when 表字段=?占位符 then
        if (whenClause.getWhenExpression() instanceof BinaryExpression) {
            //如果有一边表达式是 特殊的占位符，则维护占位符对应的表字段信息
            JsqlparserUtil.parseWhereColumTable(this.getLayer(),
                    this.getLayerFieldTableMap(),
                    (BinaryExpression) whenClause.getWhenExpression(),
                    this.getPlaceholderColumnTableMap());
        }

        //对应case when 的情况3  ：... then 表字段>= ?占位符
        Expression thenExpression = whenClause.getThenExpression();
        if (thenExpression instanceof BinaryExpression) {
            //如果有一边表达式是 特殊的占位符，则维护占位符对应的表字段信息
            JsqlparserUtil.parseWhereColumTable(this.getLayer(),
                    this.getLayerFieldTableMap(),
                    (BinaryExpression) thenExpression,
                    this.getPlaceholderColumnTableMap());
        }


    }

    @Override
    public void visit(ExistsExpression existsExpression) {
        //exist 这里会走 SubSelect 子查询的逻辑
        existsExpression.getRightExpression().accept(this);
    }

    @Override
    public void visit(MemberOfExpression memberOfExpression) {

    }

    @Override
    public void visit(AnyComparisonExpression anyComparisonExpression) {

    }

    @Override
    public void visit(Concat concat) {

    }

    @Override
    public void visit(Matches matches) {

    }

    @Override
    public void visit(BitwiseAnd bitwiseAnd) {

    }

    @Override
    public void visit(BitwiseOr bitwiseOr) {

    }

    @Override
    public void visit(BitwiseXor bitwiseXor) {

    }

    @Override
    public void visit(CastExpression cast) {

    }

    @Override
    public void visit(Modulo modulo) {

    }

    @Override
    public void visit(AnalyticExpression aexpr) {

    }

    @Override
    public void visit(ExtractExpression eexpr) {

    }

    @Override
    public void visit(IntervalExpression iexpr) {

    }

    @Override
    public void visit(OracleHierarchicalExpression oexpr) {

    }

    @Override
    public void visit(RegExpMatchOperator rexpr) {

    }

    @Override
    public void visit(JsonExpression jsonExpr) {

    }

    @Override
    public void visit(JsonOperator jsonExpr) {

    }

    @Override
    public void visit(UserVariable var) {

    }

    @Override
    public void visit(NumericBind bind) {

    }

    @Override
    public void visit(KeepExpression aexpr) {

    }

    @Override
    public void visit(MySQLGroupConcat groupConcat) {

    }

    @Override
    public void visit(ExpressionList<?> expressionList) {

    }

    @Override
    public void visit(RowConstructor<?> rowConstructor) {

    }

    @Override
    public void visit(RowGetExpression rowGetExpression) {

    }

    @Override
    public void visit(OracleHint hint) {

    }

    @Override
    public void visit(TimeKeyExpression timeKeyExpression) {

    }

    @Override
    public void visit(DateTimeLiteralExpression literal) {

    }

    /**
     * NOT：取反不影响占位符所属字段，继续解析被取反的表达式
     * 栗子： where not (name = ?)
     */
    @Override
    public void visit(NotExpression aThis) {
        Expression expression = aThis.getExpression();
        if (expression != null) {
            expression.accept(this);
        }
    }

    @Override
    public void visit(NextValExpression aThis) {

    }

    @Override
    public void visit(CollateExpression aThis) {

    }

    @Override
    public void visit(SimilarToExpression aThis) {

    }

    @Override
    public void visit(ArrayExpression aThis) {

    }

    @Override
    public void visit(ArrayConstructor aThis) {

    }

    @Override
    public void visit(VariableAssignment aThis) {

    }

    @Override
    public void visit(XMLSerializeExpr aThis) {

    }

    @Override
    public void visit(TimezoneExpression aThis) {

    }

    @Override
    public void visit(JsonAggregateFunction aThis) {

    }

    @Override
    public void visit(JsonFunction aThis) {

    }

    @Override
    public void visit(ConnectByRootOperator aThis) {

    }

    @Override
    public void visit(OracleNamedFunctionParameter aThis) {

    }

    @Override
    public void visit(AllColumns allColumns) {

    }

    @Override
    public void visit(AllTableColumns allTableColumns) {

    }

    @Override
    public void visit(AllValue allValue) {

    }

    @Override
    public void visit(IsDistinctExpression isDistinctExpression) {

    }

    @Override
    public void visit(GeometryDistance geometryDistance) {

    }

    /**
     * 子查询
     * 当exist时会走子查询的逻辑
     *
     * @author luyanan
     * @since 2025/10/10
     */
    @Override
    public void visit(Select selectBody) {
        //注意：exist这种情况，层数不需要加1，这里使用的字段和上级是同一层的
        selectBody.accept(PlaceholderSelectVisitor.newInstanceCurLayer(this));
    }

    @Override
    public void visit(TranscodingFunction transcodingFunction) {

    }

    @Override
    public void visit(TrimFunction trimFunction) {

    }

    @Override
    public void visit(RangeExpression rangeExpression) {

    }

    @Override
    public void visit(TSQLLeftJoin tsqlLeftJoin) {

    }

    @Override
    public void visit(TSQLRightJoin tsqlRightJoin) {

    }
}
