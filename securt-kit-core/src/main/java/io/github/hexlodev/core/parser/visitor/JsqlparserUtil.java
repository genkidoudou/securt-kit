package io.github.hexlodev.core.parser.visitor;

import io.github.hexlodev.core.parser.constant.FieldConstant;
import io.github.hexlodev.core.parser.dto.ColumnTableDto;
import io.github.hexlodev.core.parser.dto.FieldInfoDto;
import io.github.hexlodev.core.utils.CollectionUtils;
import io.github.hexlodev.core.utils.StringUtils;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * JSQLParser 解析辅助工具
 * 职责：
 * 1) 将列解析为 ColumnTableDto（parseColumn）
 * 2) 在二元/左右表达式上解析占位符与列的映射（parseWhereColumTable）
 * 3) 统一维护 select/表字段信息到分层 Map（putFieldInfo）
 * 说明：insertFieldIndex 仅在 INSERT VALUES 场景下传入，用于记录字段与参数位置
 */
public class JsqlparserUtil {

    /**
     * 解析列信息
     */
    public static ColumnTableDto parseColumn(Column column, int layer, Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap) {
        //字段名
        String columName = column.getColumnName();
        //字段所属表 （只有select 别名.字段名 时这个才有值，其它的为null）
        Table table = column.getTable();

        //字段所属的表的别名(from 后面接的表的别名)
        AtomicReference<String> tableAliasName = new AtomicReference<>();
        //字段所属表的真实表的名字
        AtomicReference<String> sourceTableName = new AtomicReference<>();
        //字段所属真实字段名
        AtomicReference<String> sourceColumn = new AtomicReference<>();
        //字段所属表的真实名字 from 后面的表的名字 （tableAliasName的真实名字）
        AtomicBoolean fromSourceTable = new AtomicBoolean(false);


        //1.没有指定表名时，从当前层的表的所有字段里面找到这个名字的表( select 字段)
        if (table == null) {
            layerFieldTableMap.get(String.valueOf(layer)).entrySet().forEach(f -> {
                List<FieldInfoDto> matchFields = f.getValue().stream().filter(fi -> StringUtils.equalIgnoreFieldSymbol(fi.getColumnName(), columName)).collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(matchFields)) {
                    //当前层的所有字段里面叫这个的，正确sql语法中只会有一个，所以get(0)
                    FieldInfoDto matchField = matchFields.get(0);
                    sourceTableName.set(matchField.getSourceTableName());
                    sourceColumn.set(matchField.getSourceColumn());
                    fromSourceTable.set(matchField.isFromSourceTable());
                    tableAliasName.set(f.getKey());
                }
            });
        }

        //2.有指定表名时，从当前层的这张表的所有字段里面这个字段的信息 （select 别名.字段）
        if (table != null) {
            String columnTableName = table.getName().toLowerCase();
            List<FieldInfoDto> matchFields = Optional.ofNullable(CollectionUtils.getValueIgnoreFloat(layerFieldTableMap.get(String.valueOf(layer)), columnTableName)).orElse(new HashSet<>()).stream().filter(f -> StringUtils.equalIgnoreFieldSymbol(f.getColumnName(), columName)).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(matchFields)) {
                //当前层的所有字段里面叫这个的，正确sql语法中只会有一个，所以get(0)
                FieldInfoDto matchField = matchFields.get(0);
                sourceTableName.set(matchField.getSourceTableName());
                sourceColumn.set(matchField.getSourceColumn());
                fromSourceTable.set(matchField.isFromSourceTable());
                tableAliasName.set(columnTableName);
            }
        }

        return ColumnTableDto.builder().tableAliasName(tableAliasName.get()).sourceTableName(sourceTableName.get()).sourceColumn(sourceColumn.get()).fromSourceTable(fromSourceTable.get()).build();
    }

    /**
     * 将 dto 存放到对应的layerTableMap 中
     * @param layerTableMap key: layer  value( key: tableName value: dto)
     * @author liutangqi
     * @date 2024/3/18 14:17
     **/
    public static void putFieldInfo(Map<String, Map<String, Set<FieldInfoDto>>> layerTableMap, int layer, String tableName, FieldInfoDto dto) {
        Map<String, Set<FieldInfoDto>> layerFieldMap = Optional.ofNullable(layerTableMap.get(String.valueOf(layer))).orElse(new HashMap<>());
        Set<FieldInfoDto> fieldInfoDtos = Optional.ofNullable(CollectionUtils.getValueIgnoreFloat(layerFieldMap, tableName)).orElse(new HashSet<>());

        fieldInfoDtos.add(dto);
        layerFieldMap.put(tableName, fieldInfoDtos);
        layerTableMap.put(String.valueOf(layer), layerFieldMap);
    }

    /**
     * 将 dto 存放到对应的layerTableMap 中
     * @param layerTableMap key: layer  value( key: tableName value: dtos)
     * @author liutangqi
     * @date 2024/3/18 14:17
     **/
    public static void putFieldInfo(Map<String, Map<String, Set<FieldInfoDto>>> layerTableMap, int layer, String tableName, Set<FieldInfoDto> dtos) {
        Map<String, Set<FieldInfoDto>> layerFieldMap = Optional.ofNullable(layerTableMap.get(String.valueOf(layer))).orElse(new HashMap<>());
        Set<FieldInfoDto> fieldInfoDtos = Optional.ofNullable(CollectionUtils.getValueIgnoreFloat(layerFieldMap, tableName)).orElse(new HashSet<>());

        fieldInfoDtos.addAll(dtos);
        layerFieldMap.put(tableName, fieldInfoDtos);
        layerTableMap.put(String.valueOf(layer), layerFieldMap);
    }

    /**
     * 处理POJO模式的二元表达式，左右表达式递归访问
     */
    public static void visitPojoBinaryExpression(PlaceholderExpressionVisitor visitor, BinaryExpression binaryExpression) {
        Expression leftExpression = binaryExpression.getLeftExpression();
        Expression rightExpression = binaryExpression.getRightExpression();

        // 处理左右表达式
        if (leftExpression != null) {
            leftExpression.accept(visitor);
        }
        if (rightExpression != null) {
            rightExpression.accept(visitor);
        }
    }
    /**
     * 解析WHERE条件（BinaryExpression）中的占位符-字段映射
     */
    public static void parseWhereColumTable(int layer,
                                            Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap,
                                            BinaryExpression expression,
                                            Map<String, ColumnTableDto> placeholderColumnTableMap) {
        Expression leftExpression = expression.getLeftExpression();
        Expression rightExpression = expression.getRightExpression();

        parseWhereColumTable(layer, layerFieldTableMap, leftExpression, rightExpression, placeholderColumnTableMap);
    }

    /**
     * 解析WHERE条件（支持 INSERT 字段索引）中的占位符-字段映射
     * @param insertFieldIndex INSERT 语句下的字段索引（可为 null）
     */
    public static void parseWhereColumTable(int layer,
                                            Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap,
                                            BinaryExpression expression,
                                            Map<String, ColumnTableDto> placeholderColumnTableMap,
                                            Integer insertFieldIndex) {
        Expression leftExpression = expression.getLeftExpression();
        Expression rightExpression = expression.getRightExpression();

        parseWhereColumTable(layer, layerFieldTableMap, leftExpression, rightExpression, placeholderColumnTableMap, insertFieldIndex);
    }

    /**
     * 解析WHERE条件（左右表达式）中的占位符-字段映射
     */
    public static void parseWhereColumTable(int layer,
                                            Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap,
                                            Expression leftExpression,
                                            Expression rightExpression,
                                            Map<String, ColumnTableDto> placeholderColumnTableMap) {
        parseWhereColumTable(layer, layerFieldTableMap, leftExpression, rightExpression, placeholderColumnTableMap, null);
    }

    /**
     * 解析WHERE条件（支持 INSERT 字段索引）中的占位符-字段映射
     * @param insertFieldIndex INSERT 语句下的字段索引（可为 null）
     */
    public static void parseWhereColumTable(int layer,
                                            Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap,
                                            Expression leftExpression,
                                            Expression rightExpression,
                                            Map<String, ColumnTableDto> placeholderColumnTableMap,
                                            Integer insertFieldIndex) {
        // 处理左右表达式的映射关系
        // 左边是列，右边是我们的占位符
        if (leftExpression instanceof Column && rightExpression != null && rightExpression.toString().contains(FieldConstant.PLACEHOLDER)) {
            Column column = (Column) leftExpression;
            String placeholder = rightExpression.toString();
            ColumnTableDto columnTableDto = parseColumn(column, layer, layerFieldTableMap);
            if (columnTableDto != null) {
                // 设置 INSERT 字段索引
                if (insertFieldIndex != null) {
                    columnTableDto.setInsertFieldIndex(insertFieldIndex);
                }
                placeholderColumnTableMap.put(placeholder, columnTableDto);
            }
        }

        // 左边是我们的占位符 右边是列
        if (rightExpression instanceof Column && leftExpression != null && leftExpression.toString().contains(FieldConstant.PLACEHOLDER)) {
            Column column = (Column) rightExpression;
            String placeholder = leftExpression.toString();
            ColumnTableDto columnTableDto = parseColumn(column, layer, layerFieldTableMap);
            if (columnTableDto != null) {
                // 设置 INSERT 字段索引
                if (insertFieldIndex != null) {
                    columnTableDto.setInsertFieldIndex(insertFieldIndex);
                }
                placeholderColumnTableMap.put(placeholder, columnTableDto);
            }
        }
    }

}
