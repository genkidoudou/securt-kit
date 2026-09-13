package io.github.genkidoudou.core.parser.visitor.fieldparse;

import net.sf.jsqlparser.expression.AnalyticExpression;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.CaseExpression;
import net.sf.jsqlparser.expression.CastExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.TranscodingFunction;
import net.sf.jsqlparser.expression.TrimFunction;
import net.sf.jsqlparser.expression.WhenClause;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;

import java.util.List;

/**
 * SELECT 投影中函数/CAST/CASE/窗口/标量子查询等表达式的列抽取与结果列标签规范化。
 */
public final class SelectExpressionColumnSupport {

    private SelectExpressionColumnSupport() {
    }

    /**
     * 压缩空白并转小写，用于匹配驱动返回的 columnLabel 与 parser toString()。
     */
    public static String normalizeLabel(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(raw.length());
        boolean prevSpace = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!prevSpace && sb.length() > 0) {
                    sb.append(' ');
                    prevSpace = true;
                }
                continue;
            }
            sb.append(Character.toLowerCase(c));
            prevSpace = false;
        }
        // trim trailing space
        int end = sb.length();
        while (end > 0 && sb.charAt(end - 1) == ' ') {
            end--;
        }
        return sb.substring(0, end);
    }

    /**
     * 去掉全部空白后的小写形式，进一步容忍驱动与 parser 空格差异。
     */
    public static String stripWhitespaceLower(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (!Character.isWhitespace(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * 深度优先取表达式中第一个列引用；多列时取第一个（产品约定）。
     * <p>CASE 优先 THEN/ELSE 结果列；窗口函数取主表达式列；标量子查询取内层第一投影项。</p>
     */
    public static Column findFirstColumn(Expression expression) {
        if (expression == null) {
            return null;
        }
        if (expression instanceof Column) {
            return (Column) expression;
        }
        if (expression instanceof Parenthesis) {
            return findFirstColumn(((Parenthesis) expression).getExpression());
        }
        if (expression instanceof SignedExpression) {
            return findFirstColumn(((SignedExpression) expression).getExpression());
        }
        if (expression instanceof Function) {
            ExpressionList<?> parameters = ((Function) expression).getParameters();
            if (parameters != null) {
                for (Object parameter : parameters) {
                    if (parameter instanceof Expression) {
                        Column found = findFirstColumn((Expression) parameter);
                        if (found != null) {
                            return found;
                        }
                    }
                }
            }
            return null;
        }
        if (expression instanceof CastExpression) {
            return findFirstColumn(((CastExpression) expression).getLeftExpression());
        }
        if (expression instanceof TrimFunction) {
            return findFirstColumn(((TrimFunction) expression).getExpression());
        }
        if (expression instanceof TranscodingFunction) {
            return findFirstColumn(((TranscodingFunction) expression).getExpression());
        }
        if (expression instanceof AnalyticExpression) {
            return findFirstColumn(((AnalyticExpression) expression).getExpression());
        }
        if (expression instanceof CaseExpression) {
            return findFirstColumnFromCase((CaseExpression) expression);
        }
        if (expression instanceof ParenthesedSelect) {
            return findFirstColumnFromSelect(((ParenthesedSelect) expression).getSelect());
        }
        if (expression instanceof Select) {
            return findFirstColumnFromSelect((Select) expression);
        }
        if (expression instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expression;
            Column left = findFirstColumn(binary.getLeftExpression());
            if (left != null) {
                return left;
            }
            return findFirstColumn(binary.getRightExpression());
        }
        return null;
    }

    private static Column findFirstColumnFromCase(CaseExpression caseExpression) {
        List<WhenClause> whenClauses = caseExpression.getWhenClauses();
        if (whenClauses != null) {
            for (WhenClause whenClause : whenClauses) {
                if (whenClause == null) {
                    continue;
                }
                Column thenCol = findFirstColumn(whenClause.getThenExpression());
                if (thenCol != null) {
                    return thenCol;
                }
            }
        }
        Column elseCol = findFirstColumn(caseExpression.getElseExpression());
        if (elseCol != null) {
            return elseCol;
        }
        return findFirstColumn(caseExpression.getSwitchExpression());
    }

    private static Column findFirstColumnFromSelect(Select select) {
        if (select == null) {
            return null;
        }
        if (select instanceof ParenthesedSelect) {
            return findFirstColumnFromSelect(((ParenthesedSelect) select).getSelect());
        }
        if (select instanceof PlainSelect) {
            return findFirstColumnFromPlainSelect((PlainSelect) select);
        }
        if (select instanceof SetOperationList) {
            List<Select> selects = ((SetOperationList) select).getSelects();
            if (selects != null) {
                for (Select part : selects) {
                    Column found = findFirstColumnFromSelect(part);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    private static Column findFirstColumnFromPlainSelect(PlainSelect plainSelect) {
        List<SelectItem<?>> selectItems = plainSelect.getSelectItems();
        if (selectItems == null || selectItems.isEmpty()) {
            return null;
        }
        SelectItem<?> first = selectItems.get(0);
        if (first == null || first.getExpression() == null) {
            return null;
        }
        return findFirstColumn(first.getExpression());
    }

    static boolean isWrappedColumnExpression(Expression expression) {
        if (expression == null || expression instanceof Column) {
            return false;
        }
        if (expression instanceof Parenthesis) {
            return isWrappedColumnExpression(((Parenthesis) expression).getExpression());
        }
        return expression instanceof Function
                || expression instanceof CastExpression
                || expression instanceof TrimFunction
                || expression instanceof TranscodingFunction
                || expression instanceof BinaryExpression
                || expression instanceof CaseExpression
                || expression instanceof AnalyticExpression
                || expression instanceof ParenthesedSelect
                || expression instanceof Select
                || expression instanceof SignedExpression;
    }

    static String resultKey(AliasOrNull alias, Expression selectItemExpression) {
        if (alias != null && alias.name != null && !alias.name.trim().isEmpty()) {
            return alias.name.trim();
        }
        if (selectItemExpression == null) {
            return null;
        }
        return normalizeLabel(selectItemExpression.toString());
    }

    /** 轻量别名载体，避免 visitor 依赖循环。 */
    static final class AliasOrNull {
        final String name;

        AliasOrNull(String name) {
            this.name = name;
        }
    }
}
