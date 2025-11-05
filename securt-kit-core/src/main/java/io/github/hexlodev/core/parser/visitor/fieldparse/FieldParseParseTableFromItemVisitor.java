package io.github.hexlodev.core.parser.visitor.fieldparse;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import io.github.hexlodev.core.TableCache;
import io.github.hexlodev.core.parser.dto.FieldInfoDto;
import io.github.hexlodev.core.parser.visitor.BaseFieldParseTable;
import io.github.hexlodev.core.parser.visitor.JsqlparserUtil;
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
    
    /**
     * 从表名中提取纯表名（去掉数据库名和schema前缀）
     * 
     * <p>支持的表名格式：</p>
     * <ul>
     *   <li>{@code database.table} -> {@code table}</li>
     *   <li>{@code schema.table} -> {@code table}</li>
     *   <li>{@code database.schema.table} -> {@code table}</li>
     *   <li>{@code table} -> {@code table}（已经是纯表名）</li>
     * </ul>
     *
     * @param tableName 表名，可能包含数据库名和schema前缀
     * @return 纯表名（小写），如果输入为空则返回原值
     */
    private static String extractPureTableName(String tableName) {
        if (StrUtil.isBlank(tableName)) {
            return tableName;
        }
        
        // 转换为小写
        String lowerTableName = tableName.toLowerCase().trim();
        
        // 如果包含点号，取最后一个点号后的部分作为表名
        int lastDotIndex = lowerTableName.lastIndexOf('.');
        if (lastDotIndex >= 0 && lastDotIndex < lowerTableName.length() - 1) {
            return lowerTableName.substring(lastDotIndex + 1);
        }
        
        // 如果没有点号，说明已经是纯表名
        return lowerTableName;
    }
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
        
        // 提取纯表名（去掉数据库名和schema前缀），用于 TableCache 查找
        String pureTableName = extractPureTableName(tableName);
        
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
                            pureTableName,  // sourceTableName：使用纯表名，便于后续匹配
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

    /**
     * 处理子查询作为 FROM 项的情况：例如 from (SELECT ...) tmp
     * 1. 先解析子查询的 SELECT 字段（使用下一层的 FieldParseParseTableSelectVisitor）
     * 2. 获取子查询的字段信息（从子查询层的 layerSelectTableFieldMap 中获取）
     * 3. 将子查询的字段映射到当前层的 layerFieldTableMap 中，使用子查询的别名（如果有）作为表名
     */
    @Override
    public void visit(ParenthesedSelect parenthesedSelect) {
        // 1. 先解析子查询的 SELECT 字段（使用下一层的 FieldParseParseTableSelectVisitor）
        FieldParseParseTableSelectVisitor subSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceNextLayer(this);
        Select subSelect = parenthesedSelect.getSelect();
        if (subSelect != null) {
            subSelect.accept(subSelectVisitor);
        }

        // 2. 获取子查询的别名，如果没有别名则使用默认名称
        Alias alias = parenthesedSelect.getAlias();
        String subQueryAlias = alias != null ? alias.getName() : "SUBQUERY";
        subQueryAlias = subQueryAlias.toLowerCase();

        // 3. 从子查询层的 layerSelectTableFieldMap 中获取所有字段
        // 子查询层的所有 SELECT 字段都作为当前层的可用字段
        int subQueryLayer = this.getLayer() + 1;
        Map<String, Map<String, Set<FieldInfoDto>>> layerSelectTableFieldMap = subSelectVisitor.getLayerSelectTableFieldMap();
        Map<String, Set<FieldInfoDto>> subQuerySelectFields = layerSelectTableFieldMap.getOrDefault(String.valueOf(subQueryLayer), new HashMap<>());

        // 4. 将所有子查询的字段合并到一个集合中
        Set<FieldInfoDto> allSubQueryFields = new HashSet<>();
        for (Map.Entry<String, Set<FieldInfoDto>> entry : subQuerySelectFields.entrySet()) {
            allSubQueryFields.addAll(entry.getValue());
        }

        // 5. 将子查询的字段映射到当前层的 layerFieldTableMap 中，使用子查询的别名作为表名
        if (!allSubQueryFields.isEmpty()) {
            JsqlparserUtil.putFieldInfo(this.getLayerFieldTableMap(), this.getLayer(), subQueryAlias, allSubQueryFields);
        }
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
