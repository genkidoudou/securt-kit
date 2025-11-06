package io.github.hexlodev.core.parser.visitor;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import io.github.hexlodev.core.TableCache;
import io.github.hexlodev.core.parser.constant.NumberConstant;
import io.github.hexlodev.core.parser.dto.ColumnTableDto;
import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
import io.github.hexlodev.core.parser.dto.FieldInfoDto;
import io.github.hexlodev.core.parser.visitor.fieldparse.FieldParseParseTableFromItemVisitor;
import io.github.hexlodev.core.parser.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import io.github.hexlodev.core.parser.visitor.fieldparse.PlaceholderSelectVisitor;
import io.github.hexlodev.core.utils.CollectionUtils;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.*;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.alter.AlterSession;
import net.sf.jsqlparser.statement.alter.AlterSystemStatement;
import net.sf.jsqlparser.statement.alter.RenameTableStatement;
import net.sf.jsqlparser.statement.alter.sequence.AlterSequence;
import net.sf.jsqlparser.statement.analyze.Analyze;
import net.sf.jsqlparser.statement.comment.Comment;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.schema.CreateSchema;
import net.sf.jsqlparser.statement.create.sequence.CreateSequence;
import net.sf.jsqlparser.statement.create.synonym.CreateSynonym;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.view.AlterView;
import net.sf.jsqlparser.statement.create.view.CreateView;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.execute.Execute;
import net.sf.jsqlparser.statement.grant.Grant;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.merge.Merge;
import net.sf.jsqlparser.statement.refresh.RefreshMaterializedViewStatement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.show.ShowIndexStatement;
import net.sf.jsqlparser.statement.show.ShowTablesStatement;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;
import net.sf.jsqlparser.statement.upsert.Upsert;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 通过对 pojo 进行加/解密的 SQL 解析入口
 * 职责：
 * 1) 收集 SELECT 字段与加密策略的对应关系（FieldEncryptorInfoDto）
 * 2) 构建占位符到真实表字段的映射（placeholder → ColumnTableDto）
 */
@Slf4j
public class PoJoEncrtptorStatementVisitor implements StatementVisitor {
    /**
     * 当前sql涉及到的字段以及字段的所属表结构信息
     **/
    private List<FieldEncryptorInfoDto> fieldEncryptorInfos = new ArrayList<>();

    /**
     * 当前占位符对应的数据库表，字段信息
     * key: 占位符DecryptConstant.PLACEHOLDER + 0开始的自增序号  （这个在解析前，我们会将？的占位符统一替换成这个格式的占位符）
     * value: 这个字段所属的表字段
     */
    private Map<String, ColumnTableDto> placeholderColumnTableMap;
    
    /**
     * 数据源标识（多数据源场景）
     */
    private final String datasourceId;
    
    /**
     * 构造函数（向后兼容）
     */
    public PoJoEncrtptorStatementVisitor() {
        this.datasourceId = null;
    }
    
    /**
     * 构造函数（支持多数据源）
     * 
     * @param datasourceId 数据源标识
     */
    public PoJoEncrtptorStatementVisitor(String datasourceId) {
        this.datasourceId = StrUtil.isBlank(datasourceId) ? "default" : datasourceId;
    }

    public List<FieldEncryptorInfoDto> getFieldEncryptorInfos() {
        return fieldEncryptorInfos;
    }

    public Map<String, ColumnTableDto> getPlaceholderColumnTableMap() {
        return placeholderColumnTableMap;
    }

    @Override
    public void visit(Analyze analyze) {

    }

    @Override
    public void visit(SavepointStatement savepointStatement) {

    }

    @Override
    public void visit(RollbackStatement rollbackStatement) {

    }

    @Override
    public void visit(Comment comment) {

    }

    @Override
    public void visit(Commit commit) {

    }

    @Override
    public void visit(Delete delete) {
        //1.where 条件 不存在，则不进行加密处理（delete语句主要对delete的条件进行加密）
        Expression where = delete.getWhere();
        if (where == null) {
            return;
        }

        //2.解析涉及到的表拥有的全部字段信息
        FieldParseParseTableFromItemVisitor fieldParseTableFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();
        // from 后的表
        Table table = delete.getTable();
        table.accept(fieldParseTableFromItemVisitor);

        //join 的表
        List<Join> joins = Optional.ofNullable(delete.getJoins()).orElse(new ArrayList<>());
        for (Join join : joins) {
            FromItem rightItem = join.getRightItem();
            rightItem.accept(fieldParseTableFromItemVisitor);
        }

        //3.将where 条件进行加密
        PlaceholderExpressionVisitor placeholderWhereExpressionVisitor = PlaceholderExpressionVisitor.newInstanceCurLayer(fieldParseTableFromItemVisitor, this.placeholderColumnTableMap);
        where.accept(placeholderWhereExpressionVisitor);

        //4.结果赋值
        this.placeholderColumnTableMap = placeholderWhereExpressionVisitor.getPlaceholderColumnTableMap();
    }

    @Override
    public void visit(Update update) {
        //1.解析涉及到的表拥有的全部字段信息

        //1.解析涉及到的表拥有的全部字段信息
        FieldParseParseTableFromItemVisitor fieldParseTableFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();
        Table table = update.getTable();
        table.accept(fieldParseTableFromItemVisitor);
        //join的表
        List<Join> joins = Optional.ofNullable(update.getStartJoins()).orElse(new ArrayList<>());
        for (Join join : joins) {
            join.getRightItem().accept(fieldParseTableFromItemVisitor);
        }

        //2.初始化占位符解析的结果集
        this.placeholderColumnTableMap = new HashMap<>();

        //3.加密where 条件的数据
        Expression where = update.getWhere();
        if (where != null) {
            PlaceholderExpressionVisitor dencryptWhereFieldVisitor = PlaceholderExpressionVisitor.newInstanceCurLayer(fieldParseTableFromItemVisitor, this.getPlaceholderColumnTableMap());
            where.accept(dencryptWhereFieldVisitor);
        }
        //4.加密处理set的数据
        List<UpdateSet> updateSets = update.getUpdateSets();
//        log.info("【securt-kit】UPDATE SET数量: {}", updateSets.size());
        for (UpdateSet updateSet : updateSets) {
            List<Column> columns = updateSet.getColumns();
            ExpressionList<Expression> expressions = (ExpressionList<Expression>) updateSet.getValues();
//            log.info("【securt-kit】UPDATE SET字段数量: {}", columns.size());
            for (int i = 0; i < columns.size(); i++) {
                Column column = columns.get(i);
                Expression expression = expressions.get(i);
//                log.info("【securt-kit】UPDATE SET字段: {} = {}", column.getColumnName(), expression.toString());

                //处理左右两边表达式的占位符信息
                JsqlparserUtil.parseWhereColumTable(fieldParseTableFromItemVisitor.getLayer(),
                        fieldParseTableFromItemVisitor.getLayerFieldTableMap(),
                        column,
                        expression,
                        this.getPlaceholderColumnTableMap());
            }
        }

//        log.info("【securt-kit】UPDATE处理完成，placeholderColumnTableMap大小: {}", this.placeholderColumnTableMap.size());
        for (Map.Entry<String, ColumnTableDto> entry : this.placeholderColumnTableMap.entrySet()) {
//            log.info("【securt-kit】占位符: {} -> 表: {}, 字段: {}",
//                    entry.getKey(),
//                    entry.getValue().getSourceTableName(),
//                    entry.getValue().getSourceColumn());
        }
    }

    /**
     * 处理 INSERT：解析 INSERT 字段与 SELECT/VALUES 的对应关系，生成占位符映射
     */
    @Override
    public void visit(Insert insert) {
//        log.info("【securt-kit】开始处理INSERT语句: {}", insert.toString());

        //1.insert 的表
        Table table = insert.getTable();
//        log.info("【securt-kit】INSERT表名: {}", table.getName());

        //2.解析当前insert字段所属的表结构信息
        //2.1 获取当前insert语句中的所有字段
        List<Column> columns = insert.getColumns();
//        log.info("【securt-kit】INSERT字段数量: {}", columns != null ? columns.size() : 0);
//        if (columns != null) {
//            for (Column column : columns) {
//                log.info("【securt-kit】INSERT字段: {}", column.getColumnName());
//            }
//        }

        if (CollectionUtils.isEmpty(columns)) {
            log.warn("【securt-kit】insert 语句未指定表字段顺序，不支持自动加解密，请规范语法 原sql:{}", insert.toString());
            return;
        }
        //2.2将insert的所有字段格式进行转换
        // 提取纯表名（去掉双引号、数据库名、schema前缀）
        String tableName = table.getName();
        String pureTableName = extractPureTableName(tableName);
        Set<FieldInfoDto> fieldInfoDtos = columns.stream()
                .map(m -> new FieldInfoDto(m.getColumnName().toLowerCase(), m.getColumnName().toLowerCase(), pureTableName, true))
                .collect(Collectors.toSet());
        //2.3 将转换后的字段信息维护成 layerFieldTableMap 的数据格式
        Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap = MapUtil.<String, Map<String, Set<FieldInfoDto>>>builder()
                .put(String.valueOf(NumberConstant.ONE), MapUtil.<String, Set<FieldInfoDto>>builder()
                        .put(pureTableName, fieldInfoDtos)
                        .build())
                .build();


        //3.存放占位符信息的Map初始化
        this.placeholderColumnTableMap = new HashMap<>();


        //4.处理select
        Select select = insert.getSelect();
        //解析当前查询语句的每层表的全部字段(注意：将insert的表的字段解析结果和select的表字段合并在一起，这样下游根据上游字段进行对比时，才知道占位符对应的具体的字段所属表信息)
        FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer(layerFieldTableMap);
        select.accept(fieldParseTableSelectVisitor);

        //5.解析占位符（注意：insert 语句的前后字段有对应关系，所以这里把insert前面的字段传递给后面的visitor）
        PlaceholderSelectVisitor selectVisitor = PlaceholderSelectVisitor.newInstanceCurLayer(fieldParseTableSelectVisitor, this.getPlaceholderColumnTableMap(), columns);
        select.accept(selectVisitor);
        
        //6.提取字段加密信息（用于参数加密）
        // 对于 INSERT 语句，需要检查每个字段是否需要加密
        List<FieldEncryptorInfoDto> fieldInfos = fieldInfoDtos.stream()
                .filter(field -> StrUtil.isNotBlank(field.getSourceTableName()) && StrUtil.isNotBlank(field.getSourceColumn()))
                .map(field -> FieldEncryptorInfoDto.builder()
                        .columnName(field.getColumnName())
                        .sourceColumn(field.getSourceColumn())
                        .sourceTableName(field.getSourceTableName())
                        // 注意：这里不传递 datasourceId，因为会在 SecurtkitUtils.parseSql 中根据 datasourceId 过滤
                        .fieldEncryptor(TableCache.getTableFieldEncryptStrategy(field.getSourceTableName(), field.getSourceColumn()))
                        .build())
                .collect(Collectors.toList());
        
        // 将字段加密信息添加到结果中
        this.fieldEncryptorInfos.addAll(fieldInfos);

        //7.ON DUPLICATE KEY UPDATE 语法 此语法不用单独处理，即可兼容
//        List<Column> duplicateUpdateColumns = insert.getDuplicateUpdateColumns();
//        List<Expression> duplicateUpdateExpressionList = insert.getDuplicateUpdateExpressionList();
    }

    @Override
    public void visit(Drop drop) {

    }

    @Override
    public void visit(Truncate truncate) {

    }

    @Override
    public void visit(CreateIndex createIndex) {

    }

    @Override
    public void visit(CreateSchema createSchema) {

    }

    @Override
    public void visit(CreateTable createTable) {

    }

    @Override
    public void visit(CreateView createView) {

    }

    @Override
    public void visit(AlterView alterView) {

    }

    @Override
    public void visit(RefreshMaterializedViewStatement refreshMaterializedViewStatement) {

    }

    @Override
    public void visit(Alter alter) {

    }

    @Override
    public void visit(Statements statements) {

    }

    @Override
    public void visit(Execute execute) {

    }

    @Override
    public void visit(SetStatement setStatement) {

    }

    @Override
    public void visit(ResetStatement resetStatement) {

    }

    @Override
    public void visit(ShowColumnsStatement showColumnsStatement) {

    }

    @Override
    public void visit(ShowIndexStatement showIndexStatement) {

    }

    @Override
    public void visit(ShowTablesStatement showTablesStatement) {

    }

    @Override
    public void visit(Merge merge) {

    }

    @Override
    public void visit(Select select) {
        //1.解析select拥有的字段对应的表结构信息
        //1.1解析当前sql拥有的全部字段信息
        FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
        select.accept(fieldParseTableSelectVisitor);

        //1.2.获取sql 查询的所有字段
        Map<String, Map<String, Set<FieldInfoDto>>> layerSelectTableFieldMap = fieldParseTableSelectVisitor.getLayerSelectTableFieldMap();
        List<FieldInfoDto> selectFiles = layerSelectTableFieldMap.getOrDefault(String.valueOf(NumberConstant.ONE), new HashMap<>())
                .values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        if (log.isDebugEnabled()) {
            log.debug("visit(Select): found {} fields in SELECT statement", selectFiles.size());
            for (FieldInfoDto field : selectFiles) {
                log.debug("  Field: columnName={}, sourceColumn={}, sourceTableName={}, fromSourceTable={}",
                        field.getColumnName(), field.getSourceColumn(), field.getSourceTableName(), field.isFromSourceTable());
            }
        }

        //1.3.将每个字段从实体类上找到标注的@FieldEncryptor 注解
        // 注意：这里不设置 fieldEncryptor，因为 datasourceId 在解析时可能不可用
        // fieldEncryptor 会在 SecurtkitUtils.parseSql 的后续过滤步骤中根据 datasourceId 设置
        List<FieldEncryptorInfoDto> fieldInfos = selectFiles.stream()
                .filter(a -> StrUtil.isNotBlank(a.getSourceTableName()) && StrUtil.isNotBlank(a.getSourceColumn()))
                .map(m -> FieldEncryptorInfoDto.builder()
                        .columnName(m.getColumnName())
                        .sourceColumn(m.getSourceColumn())
                        .sourceTableName(m.getSourceTableName())
                        // 不在这里设置 fieldEncryptor，因为需要 datasourceId
                        // 会在 SecurtkitUtils.parseSql 的后续过滤步骤中设置
                        .fieldEncryptor(null)
                        .build()
                ).collect(Collectors.toList());

        if (log.isDebugEnabled()) {
            log.debug("visit(Select): created {} FieldEncryptorInfoDto objects", fieldInfos.size());
            for (FieldEncryptorInfoDto info : fieldInfos) {
                log.debug("  FieldEncryptorInfo: columnName={}, sourceColumn={}, sourceTableName={}",
                        info.getColumnName(), info.getSourceColumn(), info.getSourceTableName());
            }
        }

        //1.4.结果集赋值
        this.fieldEncryptorInfos.addAll(fieldInfos);

        //2.将#{}占位符和数据库表结构字段对应起来
        //2.1开始解析
        PlaceholderSelectVisitor placeholderSelectVisitor = PlaceholderSelectVisitor.newInstanceCurLayer(fieldParseTableSelectVisitor, null);
        select.accept(placeholderSelectVisitor);
        //2.2结果赋值
        this.placeholderColumnTableMap = placeholderSelectVisitor.getPlaceholderColumnTableMap();
    }

    @Override
    public void visit(Upsert upsert) {

    }

    @Override
    public void visit(UseStatement useStatement) {

    }

    @Override
    public void visit(Block block) {

    }

    @Override
    public void visit(DescribeStatement describeStatement) {

    }

    @Override
    public void visit(ExplainStatement explainStatement) {

    }

    @Override
    public void visit(ShowStatement showStatement) {

    }

    @Override
    public void visit(DeclareStatement declareStatement) {

    }

    @Override
    public void visit(Grant grant) {

    }

    @Override
    public void visit(CreateSequence createSequence) {

    }

    @Override
    public void visit(AlterSequence alterSequence) {

    }

    @Override
    public void visit(CreateFunctionalStatement createFunctionalStatement) {

    }

    @Override
    public void visit(CreateSynonym createSynonym) {

    }

    @Override
    public void visit(AlterSession alterSession) {

    }

    @Override
    public void visit(IfElseStatement ifElseStatement) {

    }

    @Override
    public void visit(RenameTableStatement renameTableStatement) {

    }

    @Override
    public void visit(PurgeStatement purgeStatement) {

    }

    @Override
    public void visit(AlterSystemStatement alterSystemStatement) {

    }

    @Override
    public void visit(UnsupportedStatement unsupportedStatement) {

    }

    /**
     * 从表名中提取纯表名（去掉数据库名和schema前缀，以及双引号）
     * 
     * <p>支持的表名格式：</p>
     * <ul>
     *   <li>{@code database.table} -> {@code table}</li>
     *   <li>{@code schema.table} -> {@code table}</li>
     *   <li>{@code database.schema.table} -> {@code table}</li>
     *   <li>{@code "table"} -> {@code table}（去掉双引号）</li>
     *   <li>{@code table} -> {@code table}（已经是纯表名）</li>
     * </ul>
     *
     * @param tableName 表名，可能包含数据库名、schema前缀或双引号
     * @return 纯表名（小写，已去掉双引号），如果输入为空则返回原值
     */
    private String extractPureTableName(String tableName) {
        if (StrUtil.isBlank(tableName)) {
            return tableName;
        }
        
        // 转换为小写并去除首尾空白
        String lowerTableName = tableName.toLowerCase().trim();
        
        // 去掉双引号（如果存在）
        if (lowerTableName.startsWith("\"") && lowerTableName.endsWith("\"")) {
            lowerTableName = lowerTableName.substring(1, lowerTableName.length() - 1);
        }
        
        // 如果包含点号，取最后一个点号后的部分作为表名
        int lastDotIndex = lowerTableName.lastIndexOf('.');
        if (lastDotIndex >= 0 && lastDotIndex < lowerTableName.length() - 1) {
            return lowerTableName.substring(lastDotIndex + 1);
        }
        
        // 如果没有点号，说明已经是纯表名
        return lowerTableName;
    }
}
