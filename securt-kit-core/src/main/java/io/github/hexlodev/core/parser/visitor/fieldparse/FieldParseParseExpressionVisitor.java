package io.github.hexlodev.core.parser.visitor.fieldparse;

import cn.hutool.core.util.StrUtil;
import io.github.hexlodev.core.parser.dto.ColumnTableDto;
import io.github.hexlodev.core.parser.dto.FieldInfoDto;
import io.github.hexlodev.core.parser.visitor.BaseFieldParseTable;
import io.github.hexlodev.core.parser.visitor.JsqlparserUtil;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 解析 select 列表达式：
 * - 将列/常量映射到 layerSelectTableFieldMap（别名 → 源表/列）
 * - 对 * 与 表.* 做展开：写入当前层可用字段全集
 */
public class FieldParseParseExpressionVisitor extends BaseFieldParseTable implements ExpressionVisitor {
    /**
     * 当前字段拥有的别名
     */
    private Alias alias;

    /**
     * 获取当前层实例对象
     *
     * @author liutangqi
     * @date 2025/3/4 17:29
     * @Param [baseFieldParseTable, alias]
     **/
    public static FieldParseParseExpressionVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable, Alias alias) {
        return new FieldParseParseExpressionVisitor(alias,
                baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap()
        );
    }

    private FieldParseParseExpressionVisitor(Alias alias, int layer, Map<String, Map<String, Set<FieldInfoDto>>> layerSelectTableFieldMap, Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
        this.alias = alias;
    }

    /**
     * 列：解析所属表信息并写入 layerSelectTableFieldMap
     */
    @Override
    public void visit(Column tableColumn) {
        //1.解析当前字段所属的表信息
        ColumnTableDto columnTableDto = JsqlparserUtil.parseColumn(tableColumn, this.getLayer(), this.getLayerFieldTableMap());
        //当前字段别名，别名没有取库字段名
        String aliasColumName = Optional.ofNullable(alias).map(Alias::getName).orElse(tableColumn.getColumnName());

        //2.匹配到了真实表名，则将此字段存入 layerSelectTableFieldMap
        if (StrUtil.isNotBlank(columnTableDto.getSourceTableName())) {
            FieldInfoDto fieldInfoDto = FieldInfoDto.builder().columnName(aliasColumName).sourceTableName(columnTableDto.getSourceTableName()).sourceColumn(columnTableDto.getSourceColumn()).fromSourceTable(columnTableDto.isFromSourceTable()).build();

            //将此字段存入 layerSelectTableFieldMap 中
            JsqlparserUtil.putFieldInfo(this.getLayerSelectTableFieldMap(), this.getLayer(), columnTableDto.getTableAliasName(), fieldInfoDto);
        }
        //3.未匹配到真实表名，但是存在所属表别名，说明这个字段是属于内层嵌套的常量字段
        else if (StrUtil.isNotBlank(columnTableDto.getTableAliasName())) {
            FieldInfoDto fieldInfoDto = FieldInfoDto.builder().columnName(aliasColumName).sourceTableName(null).sourceColumn(null).fromSourceTable(false).build();
            JsqlparserUtil.putFieldInfo(this.getLayerSelectTableFieldMap(), this.getLayer(), columnTableDto.getTableAliasName(), fieldInfoDto);
        }
        //4.如果当前字段没有匹配到真实表名，则此字段可能是个常量，这个时候把这个字段挂虚拟表上去，存到layerSelectTableFieldMap 中
        else {
            FieldInfoDto fieldInfoDto = FieldInfoDto.builder().columnName(aliasColumName).sourceTableName(null).sourceColumn(null).fromSourceTable(false).build();
            JsqlparserUtil.putFieldInfo(this.getLayerSelectTableFieldMap(), this.getLayer(), "FUNCTION_TMP", fieldInfoDto);
        }

    }

    /**
     * *：展开本层全部字段
     */
    @Override
    public void visit(AllColumns allColumns) {
        //本层的全部字段
        Map<String, Set<FieldInfoDto>> fieldMap = Optional.ofNullable(this.getLayerFieldTableMap().get(String.valueOf(this.getLayer()))).orElse(new HashMap<>());

        //将本层全部字段放到 select的map中
        for (Map.Entry<String, Set<FieldInfoDto>> fieldInfoEntry : fieldMap.entrySet()) {
            JsqlparserUtil.putFieldInfo(this.getLayerSelectTableFieldMap(), this.getLayer(), fieldInfoEntry.getKey(), fieldInfoEntry.getValue());
        }
    }

    /**
     * 表.*：展开指定表的全部字段
     */
    @Override
    public void visit(AllTableColumns allTableColumns) {
        //获取本层涉及到的表的全部字段
        Map<String, Set<FieldInfoDto>> fieldMap = Optional.ofNullable(this.getLayerFieldTableMap().get(String.valueOf(this.getLayer()))).orElse(new HashMap<>());
        String tableAlias = allTableColumns.getTable().getName();
        Set<FieldInfoDto> fieldInfoDtos = fieldMap.get(tableAlias);
        if (fieldInfoDtos != null) {
            JsqlparserUtil.putFieldInfo(this.getLayerSelectTableFieldMap(), this.getLayer(), tableAlias, fieldInfoDtos);
        }
    }

    // 其他方法暂时留空，只实现关键的方法
    @Override
    public void visit(StringValue stringValue) {
        //当前字段别名，别名没有取字符串名字
        String aliasColumName = Optional.ofNullable(alias).map(Alias::getName).orElse(stringValue.getValue());
        FieldInfoDto fieldInfoDto = FieldInfoDto.builder().columnName(aliasColumName).sourceTableName(null).sourceColumn(null).fromSourceTable(false).build();
        JsqlparserUtil.putFieldInfo(this.getLayerSelectTableFieldMap(), this.getLayer(), "FUNCTION_TMP", fieldInfoDto);
    }

    @Override
    public void visit(LongValue longValue) {
        // 处理长整型常量
    }

    @Override
    public void visit(DoubleValue doubleValue) {
        // 处理双精度常量
    }

    @Override
    public void visit(DateValue dateValue) {
        // 处理日期常量
    }

    @Override
    public void visit(TimeValue timeValue) {
        // 处理时间常量
    }

    @Override
    public void visit(TimestampValue timestampValue) {
        // 处理时间戳常量
    }

    @Override
    public void visit(Parenthesis parenthesis) {
        // 处理括号表达式
        if (parenthesis.getExpression() != null) {
            parenthesis.getExpression().accept(this);
        }
    }

    @Override
    public void visit(JdbcParameter jdbcParameter) {
        // 处理JDBC参数
    }

    @Override
    public void visit(JdbcNamedParameter jdbcNamedParameter) {
        // 处理命名JDBC参数
    }

    @Override
    public void visit(HexValue hexValue) {
        // 处理十六进制值
    }

    @Override
    public void visit(SignedExpression signedExpression) {
        // 处理有符号表达式
    }

    @Override
    public void visit(Addition addition) {
        // 处理加法表达式
    }

    @Override
    public void visit(Division division) {
        // 处理除法表达式
    }

    @Override
    public void visit(IntegerDivision integerDivision) {
        // 处理整数除法表达式
    }

    @Override
    public void visit(Multiplication multiplication) {
        // 处理乘法表达式
    }

    @Override
    public void visit(Subtraction subtraction) {
        // 处理减法表达式
    }

    @Override
    public void visit(AndExpression andExpression) {
        // 处理AND表达式
    }

    @Override
    public void visit(OrExpression orExpression) {
        // 处理OR表达式
    }

    @Override
    public void visit(XorExpression xorExpression) {
        // 处理XOR表达式
    }

    @Override
    public void visit(Between between) {
        // 处理BETWEEN表达式
    }

    @Override
    public void visit(OverlapsCondition overlapsCondition) {
        // 处理OVERLAPS条件
    }

    @Override
    public void visit(EqualsTo equalsTo) {
        // 处理等于表达式
    }

    @Override
    public void visit(GreaterThan greaterThan) {
        // 处理大于表达式
    }

    @Override
    public void visit(GreaterThanEquals greaterThanEquals) {
        // 处理大于等于表达式
    }

    @Override
    public void visit(InExpression inExpression) {
        // 处理IN表达式
    }

    @Override
    public void visit(FullTextSearch fullTextSearch) {
        // 处理全文搜索
    }

    @Override
    public void visit(IsNullExpression isNullExpression) {
        // 处理IS NULL表达式
    }

    @Override
    public void visit(IsBooleanExpression isBooleanExpression) {
        // 处理IS BOOLEAN表达式
    }

    @Override
    public void visit(LikeExpression likeExpression) {
        // 处理LIKE表达式
    }

    @Override
    public void visit(MinorThan minorThan) {
        // 处理小于表达式
    }

    @Override
    public void visit(MinorThanEquals minorThanEquals) {
        // 处理小于等于表达式
    }

    @Override
    public void visit(NotEqualsTo notEqualsTo) {
        // 处理不等于表达式
    }

    @Override
    public void visit(DoubleAnd doubleAnd) {
        // 处理双AND表达式
    }

    @Override
    public void visit(Contains contains) {
        // 处理CONTAINS表达式
    }

    @Override
    public void visit(ContainedBy containedBy) {
        // 处理CONTAINED BY表达式
    }

    @Override
    public void visit(GeometryDistance geometryDistance) {
        // 处理几何距离表达式
    }

    @Override
    public void visit(IsDistinctExpression isDistinctExpression) {
        // 处理IS DISTINCT表达式
    }

    @Override
    public void visit(AllValue allValue) {
        // 处理ALL VALUE表达式
    }

    @Override
    public void visit(OracleNamedFunctionParameter oracleNamedFunctionParameter) {
        // 处理Oracle命名函数参数
    }

    @Override
    public void visit(ConnectByRootOperator connectByRootOperator) {
        // 处理CONNECT BY ROOT操作符
    }

    @Override
    public void visit(JsonFunction jsonFunction) {
        // 处理JSON函数
    }

    @Override
    public void visit(JsonAggregateFunction jsonAggregateFunction) {
        // 处理JSON聚合函数
    }

    @Override
    public void visit(TimezoneExpression timezoneExpression) {
        // 处理时区表达式
    }

    @Override
    public void visit(XMLSerializeExpr xmlSerializeExpr) {
        // 处理XML序列化表达式
    }

    @Override
    public void visit(VariableAssignment variableAssignment) {
        // 处理变量赋值
    }

    @Override
    public void visit(ArrayConstructor arrayConstructor) {
        // 处理数组构造函数
    }

    @Override
    public void visit(ArrayExpression arrayExpression) {
        // 处理数组表达式
    }

    @Override
    public void visit(SimilarToExpression similarToExpression) {
        // 处理SIMILAR TO表达式
    }

    @Override
    public void visit(CollateExpression collateExpression) {
        // 处理COLLATE表达式
    }

    @Override
    public void visit(NextValExpression nextValExpression) {
        // 处理NEXTVAL表达式
    }

    @Override
    public void visit(CaseExpression caseExpression) {
        // 处理CASE表达式
    }

    @Override
    public void visit(WhenClause whenClause) {
        // 处理WHEN子句
    }

    @Override
    public void visit(ExistsExpression existsExpression) {
        // 处理EXISTS表达式
    }

    @Override
    public void visit(MemberOfExpression memberOfExpression) {
        // 处理MEMBER OF表达式
    }

    @Override
    public void visit(AnyComparisonExpression anyComparisonExpression) {
        // 处理ANY比较表达式
    }

    @Override
    public void visit(Concat concat) {
        // 处理CONCAT表达式
    }

    @Override
    public void visit(Matches matches) {
        // 处理MATCHES表达式
    }

    @Override
    public void visit(BitwiseAnd bitwiseAnd) {
        // 处理按位AND表达式
    }

    @Override
    public void visit(BitwiseOr bitwiseOr) {
        // 处理按位OR表达式
    }

    @Override
    public void visit(BitwiseXor bitwiseXor) {
        // 处理按位XOR表达式
    }

    @Override
    public void visit(CastExpression castExpression) {
        // 处理CAST表达式
    }

    @Override
    public void visit(Modulo modulo) {
        // 处理取模表达式
    }

    @Override
    public void visit(AnalyticExpression analyticExpression) {
        // 处理分析表达式
    }

    @Override
    public void visit(ExtractExpression extractExpression) {
        // 处理EXTRACT表达式
    }

    @Override
    public void visit(IntervalExpression intervalExpression) {
        // 处理INTERVAL表达式
    }

    @Override
    public void visit(OracleHierarchicalExpression oracleHierarchicalExpression) {
        // 处理Oracle层次表达式
    }

    @Override
    public void visit(RegExpMatchOperator regExpMatchOperator) {
        // 处理正则表达式匹配操作符
    }

    @Override
    public void visit(JsonExpression jsonExpression) {
        // 处理JSON表达式
    }

    @Override
    public void visit(JsonOperator jsonExpr) {
        // 处理JSON操作符
    }

    @Override
    public void visit(UserVariable var) {
        // 处理用户变量
    }

    @Override
    public void visit(NumericBind bind) {
        // 处理数字绑定
    }

    @Override
    public void visit(KeepExpression aexpr) {
        // 处理KEEP表达式
    }

    @Override
    public void visit(MySQLGroupConcat groupConcat) {
        // 处理MySQL GROUP_CONCAT
    }

    @Override
    public void visit(ExpressionList<?> expressionList) {
        // 处理表达式列表
    }

    @Override
    public void visit(RowConstructor<?> rowConstructor) {
        // 处理行构造函数
    }

    @Override
    public void visit(RowGetExpression rowGetExpression) {
        // 处理行获取表达式
    }

    @Override
    public void visit(OracleHint hint) {
        // 处理Oracle提示
    }

    @Override
    public void visit(TimeKeyExpression timeKeyExpression) {
        // 处理时间键表达式
    }

    @Override
    public void visit(DateTimeLiteralExpression dateTimeLiteralExpression) {
        // 处理日期时间字面量表达式
    }

    @Override
    public void visit(NotExpression notExpression) {
        // 处理NOT表达式
    }

    @Override
    public void visit(BitwiseRightShift bitwiseRightShift) {
        // 处理按位右移表达式
    }

    @Override
    public void visit(BitwiseLeftShift bitwiseLeftShift) {
        // 处理按位左移表达式
    }

    @Override
    public void visit(NullValue nullValue) {
        // 处理NULL值
    }

    @Override
    public void visit(Function function) {
        // 处理函数
    }

    @Override
    public void visit(Select select) {
        // 处理SELECT语句
    }

    @Override
    public void visit(ParenthesedSelect parenthesedSelect) {
        // 解析当前sql的查询字段不用管子查询
    }

    @Override
    public void visit(TranscodingFunction transcodingFunction) {
        // 处理转码函数
    }

    @Override
    public void visit(TrimFunction trimFunction) {
        // 处理TRIM函数
    }

    @Override
    public void visit(RangeExpression rangeExpression) {
        // 处理范围表达式
    }

    @Override
    public void visit(TSQLLeftJoin tsqlLeftJoin) {
        // 处理TSQL左连接
    }

    @Override
    public void visit(TSQLRightJoin tsqlRightJoin) {
        // 处理TSQL右连接
    }
}
