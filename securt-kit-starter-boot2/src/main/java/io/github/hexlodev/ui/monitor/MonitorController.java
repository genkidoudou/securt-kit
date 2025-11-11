package io.github.hexlodev.ui.monitor;

import cn.hutool.core.lang.Pair;
import io.github.hexlodev.core.TableCache;
import io.github.hexlodev.core.cache.StrategyCache;
import io.github.hexlodev.core.config.FieldEncryptorProperties;
import io.github.hexlodev.core.parser.SecurtkitUtils;
import io.github.hexlodev.core.parser.dto.ColumnTableDto;
import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
import io.github.hexlodev.ui.monitor.dto.*;
import io.github.hexlodev.ui.monitor.security.SqlValidator;
import io.github.hexlodev.ui.monitor.security.SafeInput;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import javax.validation.Valid;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 监控页面 Controller
 * 处理监控页面的所有请求（登录、加密解密接口）
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("${securtkit.monitor.path:/monitor}")
@ConditionalOnProperty(prefix = "securtkit.monitor", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties({MonitorProperties.class, FieldEncryptorProperties.class})
public class MonitorController {

    @Autowired
    private MonitorProperties properties;

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired
    private FieldEncryptorProperties fieldEncryptorProperties;

    /**
     * 检查登录状态
     */
    private boolean checkLogin(HttpSession session) {
        return session != null && session.getAttribute("monitor_logged_in") != null;
    }

    /**
     * 设置登录状态
     */
    private void setLogin(HttpSession session) {
        if (session != null) {
            session.setAttribute("monitor_logged_in", true);
        }
    }

    /**
     * 清除登录状态
     */
    private void clearLogin(HttpSession session) {
        if (session != null) {
            session.removeAttribute("monitor_logged_in");
        }
    }

    /**
     * 处理根路径，重定向到 index.html
     */
    @GetMapping("/")
    public void root(HttpServletResponse response) throws IOException {
        response.sendRedirect(properties.getPath() + "/index.html");
    }

    /**
     * 处理登录请求
     */
    @PostMapping("/login")
    public ApiResponse<?> login(@RequestParam String username,
                                @RequestParam String password,
                                HttpSession session) {
        // 验证用户名密码
        if (properties.getUsername().equals(username) &&
                properties.getPassword().equals(password)) {
            setLogin(session);
            log.info("Monitor login success: {}", username);
            return ApiResponse.success("登录成功");
        } else {
            log.warn("Monitor login failed: {}", username);
            return ApiResponse.error("用户名或密码错误");
        }
    }

    /**
     * 处理退出登录
     */
    @GetMapping("/logout")
    public void logout(HttpSession session, HttpServletResponse response) throws IOException {
        clearLogin(session);
        response.sendRedirect(properties.getPath() + "/");
    }

    /**
     * 检查登录状态接口
     */
    @GetMapping("/api/check.json")
    public ApiResponse<Map<String, Object>> checkLoginStatus(HttpSession session) {
        Map<String, Object> data = new HashMap<>();
        data.put("loggedIn", checkLogin(session));
        return ApiResponse.success(data);
    }

    /**
     * 加密接口
     */
    @PostMapping("/api/encrypt.json")
    public ApiResponse<EncryptResponse> encrypt(@Valid @RequestBody EncryptRequest request,
                                                HttpSession session) {
        // 检查登录
        if (!checkLogin(session)) {
            return ApiResponse.error(401, "未登录");
        }

        try {
            // 验证参数
            if (request.getText() == null || request.getText().trim().isEmpty()) {
                return ApiResponse.error("文本内容不能为空");
            }

            // 确定加密策略
            FieldEncryptorStrategy strategy = determineStrategy(request);
            if (strategy == null) {
                return ApiResponse.error("无法确定加密策略，请提供表名+字段名或策略类名");
            }

            // 执行加密
            String encrypted = strategy.encryption(request.getText());

            // 返回结果
            EncryptResponse response = new EncryptResponse();
            response.setOriginal(request.getText());
            response.setEncrypted(encrypted);
            response.setStrategy(strategy.getClass().getName());
            return ApiResponse.success(response, "加密成功");

        } catch (Exception e) {
            log.error("加密失败", e);
            return ApiResponse.error("加密失败: " + e.getMessage());
        }
    }

    /**
     * 解密接口
     */
    @PostMapping("/api/decrypt.json")
    public ApiResponse<DecryptResponse> decrypt(@Valid @RequestBody DecryptRequest request,
                                                HttpSession session) {
        // 检查登录
        if (!checkLogin(session)) {
            return ApiResponse.error(401, "未登录");
        }

        try {
            // 验证参数
            if (request.getText() == null || request.getText().trim().isEmpty()) {
                return ApiResponse.error("文本内容不能为空");
            }

            // 确定解密策略
            FieldEncryptorStrategy strategy = determineStrategy(request);
            if (strategy == null) {
                return ApiResponse.error("无法确定解密策略，请提供表名+字段名或策略类名");
            }

            // 执行解密
            String decrypted = strategy.decryption(request.getText());

            // 返回结果
            DecryptResponse response = new DecryptResponse();
            response.setEncrypted(request.getText());
            response.setDecrypted(decrypted);
            response.setStrategy(strategy.getClass().getName());
            return ApiResponse.success(response, "解密成功");

        } catch (Exception e) {
            log.error("解密失败", e);
            return ApiResponse.error("解密失败: " + e.getMessage());
        }
    }

    /**
     * 获取可用策略列表
     */
    @GetMapping("/api/strategies.json")
    public ApiResponse<StrategiesResponse> getStrategies(HttpSession session) {
        // 检查登录
        if (!checkLogin(session)) {
            return ApiResponse.error(401, "未登录");
        }

        try {
            StrategiesResponse response = new StrategiesResponse();

            // 获取默认策略
            try {
                FieldEncryptorStrategy defaultStrategy = StrategyCache.getStrategy(FieldEncryptorStrategy.class);
                if (defaultStrategy != null) {
                    response.setDefaultStrategy(defaultStrategy.getClass().getName());
                }
            } catch (Exception e) {
                log.debug("无法获取默认策略", e);
            }

            // 获取表策略
            Map<String, Map<String, String>> tableStrategies = new HashMap<>();
            for (String tableName : TableCache.getTables()) {
                Map<String, Class<? extends FieldEncryptorStrategy>> fieldMap =
                        TableCache.getTableFieldEncryptInfo(tableName);
                if (fieldMap != null && !fieldMap.isEmpty()) {
                    Map<String, String> fields = new HashMap<>();
                    fieldMap.forEach((fieldName, strategyClass) ->
                            fields.put(fieldName, strategyClass.getName()));
                    tableStrategies.put(tableName, fields);
                }
            }
            response.setTableStrategies(tableStrategies);

            return ApiResponse.success(response);

        } catch (Exception e) {
            log.error("获取策略列表失败", e);
            return ApiResponse.error("获取策略列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取数据源列表
     */
    @GetMapping("/api/datasources.json")
    public ApiResponse<DatasourceListResponse> getDatasources(HttpSession session) {
        // 检查登录
        if (!checkLogin(session)) {
            return ApiResponse.error(401, "未登录");
        }

        try {
            DatasourceListResponse response = new DatasourceListResponse();
            List<String> datasourceIds = new ArrayList<>();
            String defaultDatasourceId = "default";

            // 从配置中提取数据源标识
            if (fieldEncryptorProperties != null && 
                fieldEncryptorProperties.getTables() != null) {
                Set<String> uniqueDatasourceIds = new HashSet<>();
                
                for (FieldEncryptorProperties.TableConfig tableConfig : 
                     fieldEncryptorProperties.getTables()) {
                    String datasourceId = tableConfig.getDatasourceId();
                    if (datasourceId == null || datasourceId.trim().isEmpty()) {
                        datasourceId = "default";
                    }
                    uniqueDatasourceIds.add(datasourceId);
                }
                
                datasourceIds.addAll(uniqueDatasourceIds);
                
                // 如果没有找到任何数据源，使用默认值
                if (datasourceIds.isEmpty()) {
                    datasourceIds.add("default");
                }
            } else {
                // 如果没有配置，返回默认数据源
                datasourceIds.add("default");
            }

            response.setDatasourceIds(datasourceIds);
            response.setDefaultDatasourceId(defaultDatasourceId);

            return ApiResponse.success(response);

        } catch (Exception e) {
            log.error("获取数据源列表失败", e);
            return ApiResponse.error("获取数据源列表失败: " + e.getMessage());
        }
    }

    /**
     * 确定使用的策略
     */
    private FieldEncryptorStrategy determineStrategy(Object request) {
        String strategyClassName = null;
        String tableName = null;
        String fieldName = null;

        if (request instanceof EncryptRequest) {
            EncryptRequest req = (EncryptRequest) request;
            strategyClassName = req.getStrategy();
            tableName = req.getTableName();
            fieldName = req.getFieldName();
        } else if (request instanceof DecryptRequest) {
            DecryptRequest req = (DecryptRequest) request;
            strategyClassName = req.getStrategy();
            tableName = req.getTableName();
            fieldName = req.getFieldName();
        }

        Class<? extends FieldEncryptorStrategy> strategyClass = null;

        // 优先级1: 直接指定的策略类名
        if (strategyClassName != null && !strategyClassName.trim().isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends FieldEncryptorStrategy> clazz =
                        (Class<? extends FieldEncryptorStrategy>) Class.forName(strategyClassName);
                strategyClass = clazz;
            } catch (Exception e) {
                log.warn("无法加载策略类: {}", strategyClassName, e);
                return null;
            }
        }
        // 优先级2: 通过表名和字段名获取策略
        else if (tableName != null && !tableName.trim().isEmpty() &&
                fieldName != null && !fieldName.trim().isEmpty()) {
            Map<String, Class<? extends FieldEncryptorStrategy>> fieldMap = 
                    TableCache.getTableFieldEncryptInfo(tableName, null);
            if (fieldMap != null) {
                strategyClass = fieldMap.get(fieldName);
            }
        }
        // 优先级3: 使用默认策略
        else {
            try {
                FieldEncryptorStrategy defaultStrategy = StrategyCache.getStrategy(FieldEncryptorStrategy.class);
                if (defaultStrategy != null) {
                    return defaultStrategy;
                }
            } catch (Exception e) {
                log.debug("无法获取默认策略", e);
            }
        }

        // 获取策略实例
        if (strategyClass != null) {
            return StrategyCache.getStrategy(strategyClass);
        }

        return null;
    }

    /**
     * SQL 解析接口
     * 调用 SecurtkitUtils.doParseSql 方法解析 SQL
     */
    @PostMapping("/api/parse-sql.json")
    public ApiResponse<ParseSqlResponse> parseSql(@Valid @RequestBody ParseSqlRequest request,
                                                   HttpSession session) {
        // 检查登录
        if (!checkLogin(session)) {
            return ApiResponse.error(401, "未登录");
        }

        try {
            // 验证参数
            if (request.getSql() == null || request.getSql().trim().isEmpty()) {
                return ApiResponse.error("SQL 语句不能为空");
            }

            String sql = request.getSql().trim();
            
            // SQL 验证
            SqlValidator.ValidationResult validation = SqlValidator.validateDmlSql(sql);
            if (!validation.isValid()) {
                return ApiResponse.error(validation.getErrorMessage());
            }

            // 使用反射调用私有的 doParseSql 方法
            Method doParseSqlMethod = SecurtkitUtils.class.getDeclaredMethod("doParseSql", String.class);
            doParseSqlMethod.setAccessible(true);
            @SuppressWarnings("unchecked")
            Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result =
                    (Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>>)
                            doParseSqlMethod.invoke(null, request.getSql());

            // 转换结果
            ParseSqlResponse response = new ParseSqlResponse();

            // 转换占位符映射
            Map<String, ParseSqlResponse.PlaceholderInfo> placeholderMap = new LinkedHashMap<>();
            if (result.getKey() != null) {
                for (Map.Entry<String, ColumnTableDto> entry : result.getKey().entrySet()) {
                    String placeholderKey = entry.getKey();
                    ColumnTableDto dto = entry.getValue();

                    ParseSqlResponse.PlaceholderInfo info = new ParseSqlResponse.PlaceholderInfo();
                    // 提取占位符索引
                    String placeholderPrefix = "SECURT_KIT_PLACEHOLDER_";
                    if (placeholderKey.startsWith(placeholderPrefix)) {
                        try {
                            Integer index = Integer.parseInt(
                                    placeholderKey.substring(placeholderPrefix.length()));
                            info.setIndex(index);
                        } catch (NumberFormatException e) {
                            log.warn("无法解析占位符索引: {}", placeholderKey);
                        }
                    }
                    info.setTableAliasName(dto.getTableAliasName());
                    info.setSourceTableName(dto.getSourceTableName());
                    info.setSourceColumn(dto.getSourceColumn());
                    info.setFromSourceTable(dto.isFromSourceTable());
                    info.setInsertFieldIndex(dto.getInsertFieldIndex());
                    info.setParameterProperty(dto.getParameterProperty());
                    info.setParameterType(dto.getParameterType());

                    placeholderMap.put(placeholderKey, info);
                }
            }
            response.setPlaceholderMap(placeholderMap);

            // 转换加密字段列表
            List<ParseSqlResponse.EncryptFieldInfo> encryptFields = new ArrayList<>();
            if (result.getValue() != null) {
                for (FieldEncryptorInfoDto dto : result.getValue()) {
                    ParseSqlResponse.EncryptFieldInfo info = new ParseSqlResponse.EncryptFieldInfo();
                    info.setColumnName(dto.getColumnName());
                    info.setSourceColumn(dto.getSourceColumn());
                    info.setSourceTableName(dto.getSourceTableName());
                    if (dto.getFieldEncryptor() != null) {
                        info.setFieldEncryptor(dto.getFieldEncryptor().getName());
                    }
                    encryptFields.add(info);
                }
            }
            response.setEncryptFields(encryptFields);

            return ApiResponse.success(response);

        } catch (NoSuchMethodException e) {
            log.error("找不到 doParseSql 方法", e);
            return ApiResponse.error("解析失败: 找不到解析方法");
        } catch (Exception e) {
            log.error("SQL 解析失败", e);
            return ApiResponse.error("SQL 解析失败: " + e.getMessage());
        }
    }

    /**
     * SQL参数加密接口
     * 将 SQL 中需要加密的字段值进行加密，支持 INSERT、UPDATE、DELETE、SELECT 等所有 SQL 类型
     */
    @PostMapping("/api/encrypt-sql.json")
    public ApiResponse<EncryptSqlResponse> encryptSql(@Valid @RequestBody EncryptSqlRequest request,
                                                      HttpSession session) {
        // 检查登录
        if (!checkLogin(session)) {
            return ApiResponse.error(401, "未登录");
        }

        try {
            // 验证参数
            if (request.getSql() == null || request.getSql().trim().isEmpty()) {
                return ApiResponse.error("SQL 语句不能为空");
            }

            String originalSql = request.getSql().trim();
            
            // SQL 验证
            SqlValidator.ValidationResult validation = SqlValidator.validateDmlSql(originalSql);
            if (!validation.isValid()) {
                return ApiResponse.error(validation.getErrorMessage());
            }
            
            String encryptedSql = encryptSqlValues(originalSql);

            EncryptSqlResponse response = new EncryptSqlResponse();
            response.setOriginalSql(originalSql);
            response.setEncryptedSql(encryptedSql);
            
            // 计算加密的字段数量（通过比较原始SQL和加密后SQL的差异）
            int encryptedCount = countEncryptedFields(originalSql, encryptedSql);
            response.setEncryptedFieldCount(encryptedCount);

            return ApiResponse.success(response);

        } catch (JSQLParserException e) {
            log.error("SQL 解析失败", e);
            return ApiResponse.error("SQL 解析失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("SQL参数加密失败", e);
            return ApiResponse.error("SQL参数加密失败: " + e.getMessage());
        }
    }

    /**
     * 加密 SQL 中的字段值
     * 支持 INSERT、UPDATE、DELETE、SELECT 等所有 SQL 类型
     */
    private String encryptSqlValues(String sql) throws JSQLParserException {
        // 解析 SQL
        Statement statement = CCJSqlParserUtil.parse(sql);
        
        // 存储需要替换的值：原值 -> 加密后的值
        Map<String, String> valueReplacements = new LinkedHashMap<>();
        String tableName = null;
        
        // 根据不同的 SQL 类型进行处理
        if (statement instanceof Update) {
            // UPDATE 语句
            Update update = (Update) statement;
            tableName = update.getTable().getName();
            log.debug("解析 UPDATE SQL，表名: {}", tableName);
            
            encryptUpdateValues(update, tableName, valueReplacements);
            
        } else if (statement instanceof net.sf.jsqlparser.statement.insert.Insert) {
            // INSERT 语句
            net.sf.jsqlparser.statement.insert.Insert insert = (net.sf.jsqlparser.statement.insert.Insert) statement;
            tableName = insert.getTable().getName();
            log.debug("解析 INSERT SQL，表名: {}", tableName);
            
            encryptInsertValues(insert, tableName, valueReplacements);
            
        } else if (statement instanceof net.sf.jsqlparser.statement.delete.Delete) {
            // DELETE 语句（WHERE 条件中的值）
            net.sf.jsqlparser.statement.delete.Delete delete = (net.sf.jsqlparser.statement.delete.Delete) statement;
            tableName = delete.getTable().getName();
            log.debug("解析 DELETE SQL，表名: {}", tableName);
            
            encryptDeleteValues(delete, tableName, valueReplacements);
            
        } else if (statement instanceof Select) {
            // SELECT 语句（WHERE 条件中的值）
            Select select = (Select) statement;
            log.debug("解析 SELECT SQL");
            
            encryptSelectValues(select, valueReplacements);
            
        } else {
            throw new IllegalArgumentException("不支持的 SQL 类型: " + statement.getClass().getSimpleName());
        }
        
        log.debug("共找到 {} 个需要加密的字段", valueReplacements.size());
        
        if (valueReplacements.isEmpty()) {
            log.warn("未找到需要加密的字段，请检查配置。表名: {}, 可用表: {}", tableName, TableCache.getTables());
            return sql;
        }
        
        // 替换 SQL 中的值
        String result = sql;
        for (Map.Entry<String, String> entry : valueReplacements.entrySet()) {
            String originalExpr = entry.getKey();
            String encryptedValue = entry.getValue();
            
            log.debug("尝试替换: {} -> {}", originalExpr, encryptedValue);
            
            // 处理不同的引号格式
            String originalWithSingleQuote = originalExpr;
            String originalWithDoubleQuote = originalExpr.replace("'", "\"");
            
            // 匹配并替换单引号版本
            String quotedOriginal = Pattern.quote(originalWithSingleQuote);
            result = result.replaceAll(quotedOriginal, Matcher.quoteReplacement(encryptedValue));
            
            // 如果原始 SQL 中使用的是双引号，也需要替换
            if (sql.contains("\"")) {
                String quotedOriginalDouble = Pattern.quote(originalWithDoubleQuote);
                String encryptedWithDoubleQuote = encryptedValue.replace("'", "\"");
                result = result.replaceAll(quotedOriginalDouble, Matcher.quoteReplacement(encryptedWithDoubleQuote));
            }
        }
        
        log.debug("加密完成，原始 SQL: {}", sql);
        log.debug("加密完成，结果 SQL: {}", result);
        
        return result;
    }

    /**
     * 加密 UPDATE 语句中的字段值
     */
    private void encryptUpdateValues(Update update, String tableName, Map<String, String> valueReplacements) {
        List<UpdateSet> updateSets = update.getUpdateSets();
        log.debug("UPDATE SET 数量: {}", updateSets.size());
        
        for (UpdateSet updateSet : updateSets) {
            List<Column> columns = updateSet.getColumns();
            ExpressionList<Expression> expressions = (ExpressionList<Expression>) updateSet.getValues();
            
            for (int i = 0; i < columns.size(); i++) {
                Column column = columns.get(i);
                Expression expression = expressions.get(i);
                
                String fieldName = column.getColumnName().toLowerCase();
                encryptFieldValue(tableName, fieldName, expression, valueReplacements);
            }
        }
    }

    /**
     * 加密 INSERT 语句中的字段值
     */
    private void encryptInsertValues(net.sf.jsqlparser.statement.insert.Insert insert, String tableName, 
                                     Map<String, String> valueReplacements) {
        List<Column> columns = insert.getColumns();
        
        // INSERT INTO table VALUES (...) 的情况
        net.sf.jsqlparser.statement.select.Values values = insert.getValues();
        if (values != null) {
            ExpressionList<Expression> expressions = (ExpressionList<Expression>) values.getExpressions();
            
            // 如果是批量插入，expressions 的每个元素都是一个 ExpressionList
            if (!expressions.isEmpty() && expressions.get(0) instanceof ExpressionList) {
                // 批量插入：INSERT INTO table VALUES (v1, v2), (v3, v4), ...
                for (Object exprObj : expressions) {
                    if (exprObj instanceof ExpressionList) {
                        ExpressionList<?> exprList = (ExpressionList<?>) exprObj;
                        List<?> exprs = exprList.getExpressions();
                        
                        for (int i = 0; i < exprs.size() && (columns == null || i < columns.size()); i++) {
                            Expression expression = (Expression) exprs.get(i);
                            String fieldName = columns != null ? columns.get(i).getColumnName().toLowerCase() : null;
                            
                            if (fieldName != null) {
                                encryptFieldValue(tableName, fieldName, expression, valueReplacements);
                            }
                        }
                    }
                }
            } else {
                // 单条插入：INSERT INTO table VALUES (v1, v2, ...)
                for (int i = 0; i < expressions.size() && (columns == null || i < columns.size()); i++) {
                    Expression expression = expressions.get(i);
                    String fieldName = columns != null ? columns.get(i).getColumnName().toLowerCase() : null;
                    
                    if (fieldName != null) {
                        encryptFieldValue(tableName, fieldName, expression, valueReplacements);
                    }
                }
            }
        }
        
        // INSERT INTO table SELECT ... 的情况（子查询插入）
        // 这种情况比较复杂，暂时不处理，因为需要解析 SELECT 的结果
    }

    /**
     * 加密 DELETE 语句 WHERE 条件中的字段值
     */
    private void encryptDeleteValues(net.sf.jsqlparser.statement.delete.Delete delete, String tableName,
                                     Map<String, String> valueReplacements) {
        net.sf.jsqlparser.expression.Expression whereExpr = delete.getWhere();
        if (whereExpr != null) {
            encryptWhereExpressionValues(whereExpr, tableName, valueReplacements);
        }
    }

    /**
     * 加密 SELECT 语句 WHERE 条件中的字段值
     */
    private void encryptSelectValues(Select select, Map<String, String> valueReplacements) {
        try {
            java.lang.reflect.Method getSelectBodyMethod = select.getClass().getMethod("getSelectBody");
            Object selectBody = getSelectBodyMethod.invoke(select);
            
            if (selectBody instanceof PlainSelect) {
                PlainSelect plainSelect = (PlainSelect) selectBody;
                net.sf.jsqlparser.statement.select.FromItem fromItem = plainSelect.getFromItem();
                
                String tableName = null;
                if (fromItem instanceof net.sf.jsqlparser.schema.Table) {
                    tableName = ((net.sf.jsqlparser.schema.Table) fromItem).getName();
                }
                
                if (tableName != null) {
                    net.sf.jsqlparser.expression.Expression whereExpr = plainSelect.getWhere();
                    if (whereExpr != null) {
                        encryptWhereExpressionValues(whereExpr, tableName, valueReplacements);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("无法解析 SELECT 语句的 WHERE 条件: {}", e.getMessage());
        }
    }

    /**
     * 加密 WHERE 表达式中的字段值
     */
    private void encryptWhereExpressionValues(net.sf.jsqlparser.expression.Expression expr, String tableName,
                                            Map<String, String> valueReplacements) {
        if (expr == null) {
            return;
        }
        
        // 处理二元表达式（如 name = '张三'）
        if (expr instanceof net.sf.jsqlparser.expression.BinaryExpression) {
            net.sf.jsqlparser.expression.BinaryExpression binaryExpr = 
                (net.sf.jsqlparser.expression.BinaryExpression) expr;
            
            net.sf.jsqlparser.expression.Expression leftExpr = binaryExpr.getLeftExpression();
            net.sf.jsqlparser.expression.Expression rightExpr = binaryExpr.getRightExpression();
            
            // 检查是否是需要加密的字段条件：左边是列，右边是值
            Column column = null;
            Expression valueExpr = null;
            
            if (leftExpr instanceof Column && rightExpr instanceof Expression) {
                column = (Column) leftExpr;
                valueExpr = rightExpr;
            } else if (rightExpr instanceof Column && leftExpr instanceof Expression) {
                // 处理位置互换的情况（如 '张三' = name）
                column = (Column) rightExpr;
                valueExpr = leftExpr;
            }
            
            if (column != null && valueExpr != null) {
                String fieldName = column.getColumnName().toLowerCase();
                encryptFieldValue(tableName, fieldName, valueExpr, valueReplacements);
                return; // 处理完这个二元表达式后返回，不再递归处理左右表达式
            }
            
            // 如果没有处理当前表达式，递归处理左右表达式（用于处理 AND、OR 等复杂表达式）
            encryptWhereExpressionValues(leftExpr, tableName, valueReplacements);
            encryptWhereExpressionValues(rightExpr, tableName, valueReplacements);
        }
        // 处理括号表达式
        else if (expr instanceof net.sf.jsqlparser.expression.Parenthesis) {
            net.sf.jsqlparser.expression.Parenthesis paren = 
                (net.sf.jsqlparser.expression.Parenthesis) expr;
            encryptWhereExpressionValues(paren.getExpression(), tableName, valueReplacements);
        }
        // 处理 IN 表达式
        else if (expr instanceof net.sf.jsqlparser.expression.operators.relational.InExpression) {
            net.sf.jsqlparser.expression.operators.relational.InExpression inExpr = 
                (net.sf.jsqlparser.expression.operators.relational.InExpression) expr;
            
            net.sf.jsqlparser.expression.Expression leftExpr = inExpr.getLeftExpression();
            net.sf.jsqlparser.expression.Expression rightExpr = inExpr.getRightExpression();
            
            if (leftExpr instanceof Column && rightExpr instanceof ExpressionList) {
                Column column = (Column) leftExpr;
                String fieldName = column.getColumnName().toLowerCase();
                ExpressionList<?> exprList = (ExpressionList<?>) rightExpr;
                
                for (net.sf.jsqlparser.expression.Expression e : exprList.getExpressions()) {
                    encryptFieldValue(tableName, fieldName, e, valueReplacements);
                }
            }
        }
    }

    /**
     * 加密单个字段的值
     */
    private void encryptFieldValue(String tableName, String fieldName, Expression expression,
                                   Map<String, String> valueReplacements) {
        log.debug("检查字段: {} (表: {})", fieldName, tableName);
        
        // 检查该字段是否需要加密
        Map<String, Class<? extends FieldEncryptorStrategy>> fieldMap = 
            TableCache.getTableFieldEncryptInfo(tableName, null);
        Class<? extends FieldEncryptorStrategy> strategyClass = null;
        if (fieldMap != null) {
            strategyClass = fieldMap.get(fieldName);
        }
        
        if (strategyClass != null) {
            log.debug("字段 {} 需要加密，策略: {}", fieldName, strategyClass.getName());
            
            // 提取表达式的值
            String originalValue = extractValueFromExpression(expression);
            log.debug("字段 {} 的原始值: {}", fieldName, originalValue);
            
            if (originalValue != null) {
                // 加密值
                FieldEncryptorStrategy strategy = StrategyCache.getStrategy(strategyClass);
                String encryptedValue = strategy.encryption(originalValue);
                log.debug("字段 {} 的加密值: {}", fieldName, encryptedValue);
                
                // 记录替换关系（使用原始SQL中的格式，包括引号）
                String originalSqlValue = expression.toString();
                String encryptedSqlValue = formatSqlValue(encryptedValue);
                
                log.debug("替换: {} -> {}", originalSqlValue, encryptedSqlValue);
                valueReplacements.put(originalSqlValue, encryptedSqlValue);
            }
        } else {
            log.debug("字段 {} 不需要加密", fieldName);
        }
    }

    /**
     * 从 Expression 中提取实际值
     */
    private String extractValueFromExpression(Expression expression) {
        if (expression instanceof StringValue) {
            return ((StringValue) expression).getValue();
        } else if (expression instanceof LongValue) {
            return String.valueOf(((LongValue) expression).getValue());
        } else if (expression instanceof DoubleValue) {
            return String.valueOf(((DoubleValue) expression).getValue());
        } else if (expression instanceof NullValue) {
            return null;
        } else if (expression instanceof SignedExpression) {
            // 处理带符号的表达式，如 -123
            SignedExpression signed = (SignedExpression) expression;
            Expression innerExpr = signed.getExpression();
            String value = extractValueFromExpression(innerExpr);
            if (value != null && signed.getSign() == '-') {
                return "-" + value;
            }
            return value;
        }
        // 对于其他类型的表达式，尝试通过 toString() 获取
        // 但需要去掉引号（如果是字符串值）
        String exprStr = expression.toString();
        log.debug("表达式类型: {}, toString(): {}", expression.getClass().getName(), exprStr);
        
        // 处理单引号字符串
        if (exprStr.startsWith("'") && exprStr.endsWith("'")) {
            // 去掉引号并处理转义
            return exprStr.substring(1, exprStr.length() - 1).replace("''", "'");
        }
        // 处理双引号字符串（某些数据库支持）
        if (exprStr.startsWith("\"") && exprStr.endsWith("\"")) {
            // 去掉引号并处理转义
            return exprStr.substring(1, exprStr.length() - 1).replace("\"\"", "\"");
        }
        return exprStr;
    }

    /**
     * 格式化 SQL 值（添加引号等）
     */
    private String formatSqlValue(String value) {
        if (value == null) {
            return "NULL";
        }
        // 转义单引号
        String escaped = value.replace("'", "''");
        return "'" + escaped + "'";
    }

    /**
     * 计算加密的字段数量
     */
    private int countEncryptedFields(String originalSql, String encryptedSql) {
        if (originalSql.equals(encryptedSql)) {
            return 0;
        }
        // 简单统计：计算被替换的值的数量
        // 通过比较原始SQL和加密后SQL的差异来判断
        try {
            Statement originalStmt = CCJSqlParserUtil.parse(originalSql);
            Statement encryptedStmt = CCJSqlParserUtil.parse(encryptedSql);
            
            if (originalStmt instanceof Update && encryptedStmt instanceof Update) {
                Update originalUpdate = (Update) originalStmt;
                Update encryptedUpdate = (Update) encryptedStmt;
                
                int count = 0;
                List<UpdateSet> originalSets = originalUpdate.getUpdateSets();
                List<UpdateSet> encryptedSets = encryptedUpdate.getUpdateSets();
                
                if (originalSets.size() == encryptedSets.size()) {
                    for (int i = 0; i < originalSets.size(); i++) {
                        UpdateSet originalSet = originalSets.get(i);
                        UpdateSet encryptedSet = encryptedSets.get(i);
                        
                        List<Column> columns = originalSet.getColumns();
                        ExpressionList<Expression> originalExprs = (ExpressionList<Expression>) originalSet.getValues();
                        ExpressionList<Expression> encryptedExprs = (ExpressionList<Expression>) encryptedSet.getValues();
                        
                        for (int j = 0; j < columns.size(); j++) {
                            String originalValue = originalExprs.get(j).toString();
                            String encryptedValue = encryptedExprs.get(j).toString();
                            if (!originalValue.equals(encryptedValue)) {
                                count++;
                            }
                        }
                    }
                }
                return count;
            }
        } catch (Exception e) {
            log.debug("无法精确计算加密字段数量", e);
        }
        
        // 降级方案：粗略统计
        return 1; // 至少有一个字段被加密
    }

    /**
     * SQL 查询接口
     * 执行 SELECT 查询并返回结果（默认分页 10 条）
     */
    @PostMapping("/api/query-sql.json")
    public ApiResponse<QuerySqlResponse> querySql(@Valid @RequestBody QuerySqlRequest request,
                                                    HttpSession session) {
        // 检查登录
        if (!checkLogin(session)) {
            return ApiResponse.error(401, "未登录");
        }

        try {
            // 验证参数
            if (request.getSql() == null || request.getSql().trim().isEmpty()) {
                return ApiResponse.error("SQL 语句不能为空");
            }

            // 检查 DataSource 是否可用
            if (dataSource == null) {
                return ApiResponse.error("数据源不可用，无法执行查询");
            }

            String sql = request.getSql().trim();
            
            // 验证是否为 SELECT 语句
            if (!sql.trim().toUpperCase().startsWith("SELECT")) {
                return ApiResponse.error("只支持 SELECT 查询语句");
            }

            // SQL 验证
            SqlValidator.ValidationResult validation = SqlValidator.validateSelectSql(sql);
            if (!validation.isValid()) {
                return ApiResponse.error(validation.getErrorMessage());
            }

            // 解析 SQL 并添加分页
            int pageSize = request.getPageSize() != null && request.getPageSize() > 0 
                    ? request.getPageSize() : 10;
            int pageNum = request.getPageNum() != null && request.getPageNum() > 0 
                    ? request.getPageNum() : 1;
            int offset = (pageNum - 1) * pageSize;

            String pagedSql = addPaginationToSql(sql, pageSize, offset);
            log.debug("原始 SQL: {}", sql);
            log.debug("分页 SQL: {}", pagedSql);

            // 对 WHERE 条件中的加密字段值进行加密
            String encryptedSql = encryptWhereClauseValues(pagedSql);
            log.debug("加密后的 SQL: {}", encryptedSql);

            // 多数据源：根据请求的 datasourceId 切换数据源上下文（如果存在动态数据源）
            String requestedDsId = request.getDatasourceId();
            boolean switched = false;
            if (StringUtils.hasText(requestedDsId)) {
                try {
                    Class<?> holder = Class.forName("com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder");
                    holder.getMethod("push", String.class).invoke(null, requestedDsId);
                    switched = true;
                    log.debug("Switched datasource to '{}'", requestedDsId);
                } catch (ClassNotFoundException ignore) {
                    // 未引入 dynamic-datasource，忽略
                }
            }

            QuerySqlResponse response;
            try {
                // 执行查询
                response = executeQuery(encryptedSql, sql, pageNum, pageSize);
            } finally {
                if (switched) {
                    try {
                        Class<?> holder = Class.forName("com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder");
                        holder.getMethod("clear").invoke(null);
                        log.debug("Cleared datasource context for '{}'", requestedDsId);
                    } catch (Throwable ignore) {
                    }
                }
            }
            
            return ApiResponse.success(response);

        } catch (JSQLParserException e) {
            log.error("SQL 解析失败", e);
            return ApiResponse.error("SQL 解析失败: " + e.getMessage());
        } catch (SQLException e) {
            log.error("SQL 查询失败", e);
            return ApiResponse.error("SQL 查询失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("SQL 查询失败", e);
            return ApiResponse.error("SQL 查询失败: " + e.getMessage());
        }
    }

    /**
     * 为 SQL 添加分页
     */
    private String addPaginationToSql(String sql, int limit, int offset) throws JSQLParserException {
        Statement statement = CCJSqlParserUtil.parse(sql);
        
        if (!(statement instanceof Select)) {
            throw new IllegalArgumentException("只支持 SELECT 语句");
        }

        Select select = (Select) statement;
        
        // 使用反射或访问者模式来访问 SelectBody
        // 尝试直接访问 SelectBody（不同版本的 JSQLParser API 可能不同）
        try {
            // 尝试通过反射访问 getSelectBody 方法
            java.lang.reflect.Method getSelectBodyMethod = select.getClass().getMethod("getSelectBody");
            Object selectBody = getSelectBodyMethod.invoke(select);
            
            if (selectBody instanceof PlainSelect) {
                PlainSelect plainSelect = (PlainSelect) selectBody;
                
                // 检查是否已有 LIMIT 子句
                Limit existingLimit = plainSelect.getLimit();
                if (existingLimit == null) {
                    // 添加 LIMIT 和 OFFSET
                    Limit limitObj = new Limit();
                    limitObj.setRowCount(new LongValue(limit));
                    if (offset > 0) {
                        limitObj.setOffset(new LongValue(offset));
                    }
                    plainSelect.setLimit(limitObj);
                }
                // 如果已有 LIMIT，不修改（使用用户指定的分页）
            }
        } catch (Exception e) {
            log.warn("无法通过反射访问 SelectBody，尝试直接解析: {}", e.getMessage());
            // 如果反射失败，尝试简单的字符串追加方式
            // 这种方式不够优雅，但可以作为后备方案
            String upperSql = sql.trim().toUpperCase();
            if (!upperSql.contains("LIMIT")) {
                sql = sql.trim();
                if (sql.endsWith(";")) {
                    sql = sql.substring(0, sql.length() - 1);
                }
                if (offset > 0) {
                    sql += " LIMIT " + limit + " OFFSET " + offset;
                } else {
                    sql += " LIMIT " + limit;
                }
                return sql;
            }
        }
        
        return select.toString();
    }

    /**
     * 执行查询并返回结果
     */
    private QuerySqlResponse executeQuery(String sql, String originalSql, 
                                          int pageNum, int pageSize) throws SQLException {
        QuerySqlResponse response = new QuerySqlResponse();
        response.setExecutedSql(sql);
        response.setPageNum(pageNum);
        response.setPageSize(pageSize);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            // 获取列信息
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columns.add(metaData.getColumnLabel(i));
            }
            response.setColumns(columns);

            // 提取数据
            List<Map<String, Object>> data = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String column : columns) {
                    Object value = rs.getObject(column);
                    row.put(column, value);
                }
                data.add(row);
            }
            response.setData(data);

            // 注意：由于已经分页，这里无法准确获取总记录数
            // 如果需要总记录数，需要执行 COUNT 查询
            response.setTotalCount((long) data.size());

            log.debug("查询完成，返回 {} 条记录", data.size());
        }

        return response;
    }

    /**
     * 加密 SELECT 查询中 WHERE 条件里的字段值
     * 使用占位符方式，复用 core 项目的逻辑
     */
    private String encryptWhereClauseValues(String sql) throws JSQLParserException {
        Statement statement = CCJSqlParserUtil.parse(sql);
        
        if (!(statement instanceof Select)) {
            return sql; // 不是 SELECT 语句，直接返回
        }

        Select select = (Select) statement;
        
        try {
            // 使用反射访问 getSelectBody 方法
            java.lang.reflect.Method getSelectBodyMethod = select.getClass().getMethod("getSelectBody");
            Object selectBody = getSelectBodyMethod.invoke(select);
            
            if (selectBody instanceof PlainSelect) {
                PlainSelect plainSelect = (PlainSelect) selectBody;
                
                // 获取 WHERE 条件
                net.sf.jsqlparser.expression.Expression whereExpr = plainSelect.getWhere();
                if (whereExpr == null) {
                    return sql; // 没有 WHERE 条件，直接返回
                }
                
                // 步骤1: 提取 WHERE 条件中的值，替换为占位符
                // 存储：原始值 -> 占位符索引
                Map<String, Integer> valueToPlaceholderMap = new LinkedHashMap<>();
                List<String> values = new ArrayList<>();
                int placeholderIndex = 1;
                
                String placeholderSql = replaceWhereValuesWithPlaceholders(
                    sql, whereExpr, valueToPlaceholderMap, values, placeholderIndex);
                
                log.debug("转换为占位符 SQL: {}", placeholderSql);
                log.debug("提取的值: {}", values);
                
                // 步骤2: 使用 core 项目的逻辑解析占位符映射
                Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult = 
                    SecurtkitUtils.parseSql(placeholderSql);
                
                Map<String, ColumnTableDto> placeholderColumnMap = parseResult.getKey();
                log.debug("占位符映射数量: {}", placeholderColumnMap.size());
                
                // 步骤3: 加密每个占位符对应的值
                Map<String, String> encryptedValues = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> entry : valueToPlaceholderMap.entrySet()) {
                    String originalValue = entry.getKey();
                    Integer index = entry.getValue();
                    
                    // 查找占位符对应的字段信息
                    String placeholderKey = SecurtkitUtils.PLACEHOLDER + index;
                    ColumnTableDto columnDto = placeholderColumnMap.get(placeholderKey);
                    
                    if (columnDto != null) {
                        String tableName = columnDto.getSourceTableName();
                        String fieldName = columnDto.getSourceColumn();
                        
                        // 检查是否需要加密
                        Map<String, Class<? extends FieldEncryptorStrategy>> fieldMap = 
                            TableCache.getTableFieldEncryptInfo(tableName, null);
                        Class<? extends FieldEncryptorStrategy> strategyClass = null;
                        if (fieldMap != null) {
                            strategyClass = fieldMap.get(fieldName);
                        }
                        
                        if (strategyClass != null) {
                            try {
                                FieldEncryptorStrategy strategy = StrategyCache.getStrategy(strategyClass);
                                String encryptedValue = strategy.encryption(originalValue);
                                encryptedValues.put(originalValue, encryptedValue);
                                log.debug("字段 {} 的值 {} 加密为 {}", fieldName, originalValue, encryptedValue);
                            } catch (Exception e) {
                                log.warn("加密字段 {} 的值失败: {}", fieldName, e.getMessage());
                            }
                        }
                    }
                }
                
                // 步骤4: 替换 SQL 中的原始值为加密后的值
                String result = sql;
                for (Map.Entry<String, String> entry : encryptedValues.entrySet()) {
                    // 查找原始值在 SQL 中的位置（需要匹配引号）
                    String originalValue = entry.getKey();
                    String encryptedValue = entry.getValue();
                    
                    // 尝试匹配单引号和双引号
                    String originalWithSingleQuote = "'" + originalValue.replace("'", "''") + "'";
                    String originalWithDoubleQuote = "\"" + originalValue.replace("\"", "\"\"") + "\"";
                    String encryptedWithSingleQuote = formatSqlValue(encryptedValue);
                    
                    result = result.replace(originalWithSingleQuote, encryptedWithSingleQuote);
                    result = result.replace(originalWithDoubleQuote, encryptedWithSingleQuote);
                }
                
                return result;
            }
        } catch (Exception e) {
            log.warn("无法加密 WHERE 条件值，返回原 SQL: {}", e.getMessage(), e);
        }
        
        return sql;
    }

    /**
     * 将 WHERE 条件中的值替换为占位符
     * 使用更简单的方法：直接解析表达式，提取值并在 SQL 中替换
     */
    private String replaceWhereValuesWithPlaceholders(
            String sql,
            net.sf.jsqlparser.expression.Expression whereExpr,
            Map<String, Integer> valueToPlaceholderMap,
            List<String> values,
            int startIndex) {
        
        // 使用递归方式提取表达式中的所有值
        List<ValueInfo> valueInfos = new ArrayList<>();
        extractValuesFromExpression(whereExpr, valueInfos);
        
        if (valueInfos.isEmpty()) {
            return sql;
        }
        
        // 找到 WHERE 关键字的位置
        int whereIndex = sql.toUpperCase().indexOf("WHERE");
        if (whereIndex < 0) {
            return sql;
        }
        
        String result = sql;
        int placeholderIndex = startIndex;
        
        // 按 SQL 表示长度从长到短排序，避免部分匹配问题
        valueInfos.sort((a, b) -> Integer.compare(b.sqlRepr.length(), a.sqlRepr.length()));
        
        // 从后往前替换值（避免索引偏移）
        for (int i = valueInfos.size() - 1; i >= 0; i--) {
            ValueInfo info = valueInfos.get(i);
            
            // 在 WHERE 之后查找该值的 SQL 表示
            int pos = result.indexOf(info.sqlRepr, whereIndex);
            if (pos > whereIndex) {
                String placeholder = "?";
                result = result.substring(0, pos) + placeholder + result.substring(pos + info.sqlRepr.length());
                
                valueToPlaceholderMap.put(info.value, placeholderIndex);
                values.add(info.value);
                placeholderIndex++;
            }
        }
        
        return result;
    }

    /**
     * 递归提取表达式中的所有值
     */
    private void extractValuesFromExpression(net.sf.jsqlparser.expression.Expression expr, List<ValueInfo> values) {
        if (expr == null) {
            return;
        }
        
        // 字符串值
        if (expr instanceof StringValue) {
            StringValue sv = (StringValue) expr;
            values.add(new ValueInfo(sv.getValue(), sv.toString()));
            return;
        }
        
        // 数字值
        if (expr instanceof LongValue) {
            LongValue lv = (LongValue) expr;
            values.add(new ValueInfo(String.valueOf(lv.getValue()), lv.toString()));
            return;
        }
        
        if (expr instanceof DoubleValue) {
            DoubleValue dv = (DoubleValue) expr;
            values.add(new ValueInfo(String.valueOf(dv.getValue()), dv.toString()));
            return;
        }
        
        // 二元表达式（递归处理左右两边）
        if (expr instanceof net.sf.jsqlparser.expression.BinaryExpression) {
            net.sf.jsqlparser.expression.BinaryExpression binaryExpr = 
                (net.sf.jsqlparser.expression.BinaryExpression) expr;
            extractValuesFromExpression(binaryExpr.getLeftExpression(), values);
            extractValuesFromExpression(binaryExpr.getRightExpression(), values);
        }
        
        // 括号表达式
        if (expr instanceof net.sf.jsqlparser.expression.Parenthesis) {
            net.sf.jsqlparser.expression.Parenthesis paren = 
                (net.sf.jsqlparser.expression.Parenthesis) expr;
            extractValuesFromExpression(paren.getExpression(), values);
        }
        
        // IN 表达式
        if (expr instanceof net.sf.jsqlparser.expression.operators.relational.InExpression) {
            net.sf.jsqlparser.expression.operators.relational.InExpression inExpr = 
                (net.sf.jsqlparser.expression.operators.relational.InExpression) expr;
            net.sf.jsqlparser.expression.Expression rightExpr = inExpr.getRightExpression();
            
            // 处理 IN (value1, value2, ...) 的情况
            if (rightExpr instanceof net.sf.jsqlparser.expression.operators.relational.ExpressionList) {
                net.sf.jsqlparser.expression.operators.relational.ExpressionList<?> exprList = 
                    (net.sf.jsqlparser.expression.operators.relational.ExpressionList<?>) rightExpr;
                for (net.sf.jsqlparser.expression.Expression e : exprList.getExpressions()) {
                    extractValuesFromExpression(e, values);
                }
            }
            // 处理 IN (SELECT ...) 的情况（子查询）
            else if (rightExpr != null) {
                extractValuesFromExpression(rightExpr, values);
            }
        }
        
        // NOT 表达式
        if (expr instanceof net.sf.jsqlparser.expression.NotExpression) {
            net.sf.jsqlparser.expression.NotExpression notExpr = 
                (net.sf.jsqlparser.expression.NotExpression) expr;
            extractValuesFromExpression(notExpr.getExpression(), values);
        }
        
        // 有符号表达式
        if (expr instanceof SignedExpression) {
            SignedExpression signedExpr = (SignedExpression) expr;
            extractValuesFromExpression(signedExpr.getExpression(), values);
        }
    }

    /**
     * 值信息
     */
    private static class ValueInfo {
        String value;      // 实际值（去掉引号）
        String sqlRepr;    // SQL 中的表示（如 '值'）
        
        ValueInfo(String value, String sqlRepr) {
            this.value = value;
            this.sqlRepr = sqlRepr;
        }
    }

    /**
     * 数据初始化 - 加密接口
     * 根据表名、WHERE条件和主键字段，对数据库中的加密字段进行加密
     */
    @PostMapping("/api/data-init/encrypt.json")
    public ApiResponse<DataInitResponse> dataInitEncrypt(@Valid @RequestBody DataInitRequest request,
                                                         HttpSession session) {
        // 检查登录
        if (!checkLogin(session)) {
            return ApiResponse.error(401, "未登录");
        }

        try {
            // 检查 DataSource 是否可用
            if (dataSource == null) {
                return ApiResponse.error("数据源不可用，无法执行操作");
            }

            // 多数据源：根据请求的 datasourceId 切换数据源上下文（如果存在动态数据源）
            String requestedDsId = request.getDatasourceId();
            boolean switched = false;
            if (StringUtils.hasText(requestedDsId)) {
                try {
                    Class<?> holder = Class.forName("com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder");
                    holder.getMethod("push", String.class).invoke(null, requestedDsId);
                    switched = true;
                    log.debug("Switched datasource to '{}'", requestedDsId);
                } catch (ClassNotFoundException ignore) {
                    // 未引入 dynamic-datasource，忽略
                }
            }

            DataInitResponse response;
            try {
                // 执行数据初始化
                response = processDataInit(request, true);
            } finally {
                if (switched) {
                    try {
                        Class<?> holder = Class.forName("com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder");
                        holder.getMethod("clear").invoke(null);
                        log.debug("Cleared datasource context for '{}'", requestedDsId);
                    } catch (Throwable ignore) {
                    }
                }
            }
            return ApiResponse.success(response, "加密完成");

        } catch (Exception e) {
            log.error("数据初始化加密失败", e);
            return ApiResponse.error("数据初始化加密失败: " + e.getMessage());
        }
    }

    /**
     * 数据初始化 - 解密接口
     * 根据表名、WHERE条件和主键字段，对数据库中的加密字段进行解密
     */
    @PostMapping("/api/data-init/decrypt.json")
    public ApiResponse<DataInitResponse> dataInitDecrypt(@Valid @RequestBody DataInitRequest request,
                                                         HttpSession session) {
        // 检查登录
        if (!checkLogin(session)) {
            return ApiResponse.error(401, "未登录");
        }

        try {
            // 检查 DataSource 是否可用
            if (dataSource == null) {
                return ApiResponse.error("数据源不可用，无法执行操作");
            }

            // 多数据源：根据请求的 datasourceId 切换数据源上下文（如果存在动态数据源）
            String requestedDsId = request.getDatasourceId();
            boolean switched = false;
            if (StringUtils.hasText(requestedDsId)) {
                try {
                    Class<?> holder = Class.forName("com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder");
                    holder.getMethod("push", String.class).invoke(null, requestedDsId);
                    switched = true;
                    log.debug("Switched datasource to '{}'", requestedDsId);
                } catch (ClassNotFoundException ignore) {
                    // 未引入 dynamic-datasource，忽略
                }
            }

            DataInitResponse response;
            try {
                // 执行数据初始化
                response = processDataInit(request, false);
            } finally {
                if (switched) {
                    try {
                        Class<?> holder = Class.forName("com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder");
                        holder.getMethod("clear").invoke(null);
                        log.debug("Cleared datasource context for '{}'", requestedDsId);
                    } catch (Throwable ignore) {
                    }
                }
            }
            return ApiResponse.success(response, "解密完成");

        } catch (Exception e) {
            log.error("数据初始化解密失败", e);
            return ApiResponse.error("数据初始化解密失败: " + e.getMessage());
        }
    }

    /**
     * 处理数据初始化（加密或解密）
     *
     * @param request 请求参数
     * @param isEncrypt true=加密，false=解密
     * @return 处理结果
     */
    private DataInitResponse processDataInit(DataInitRequest request, boolean isEncrypt) throws SQLException {
        String tableName = request.getTableName().trim();
        // 规范化表名用于配置匹配：去掉首尾双引号（若存在）
        String normalizedTableName = tableName;
        if (normalizedTableName.length() >= 2 && normalizedTableName.startsWith("\"") && normalizedTableName.endsWith("\"")) {
            normalizedTableName = normalizedTableName.substring(1, normalizedTableName.length() - 1);
        }
        String whereCondition = request.getWhereCondition() != null ? request.getWhereCondition().trim() : "";
        String primaryKeyField = request.getPrimaryKeyField().trim();
        String datasourceId = request.getDatasourceId() != null ? request.getDatasourceId().trim() : null;
        
        // 如果未指定数据源，使用默认值
        if (datasourceId == null || datasourceId.isEmpty()) {
            datasourceId = "default";
        }

        DataInitResponse response = new DataInitResponse();
        response.setTableName(tableName);
        response.setPrimaryKeyField(primaryKeyField);
        response.setOperationType(isEncrypt ? "encrypt" : "decrypt");

        // 获取表需要加密的字段（支持多数据源）
        Map<String, Class<? extends FieldEncryptorStrategy>> encryptFields = TableCache.getTableFieldEncryptInfo(normalizedTableName, datasourceId);
        if (encryptFields == null || encryptFields.isEmpty()) {
            throw new IllegalArgumentException("表 " + normalizedTableName + " 在数据源 " + datasourceId + " 中没有配置需要加密的字段");
        }

        List<String> sqlStatements = new ArrayList<>();
        int processedCount = 0;
        int processedFieldCount = 0;

        // 构建查询SQL
        StringBuilder selectSql = new StringBuilder("SELECT ");
        selectSql.append(primaryKeyField);
        for (String fieldName : encryptFields.keySet()) {
            selectSql.append(", ").append(fieldName);
        }
        selectSql.append(" FROM ").append(tableName);
        if (!whereCondition.isEmpty()) {
            selectSql.append(" WHERE ").append(whereCondition);
        }

        log.debug("查询SQL: {}", selectSql);

        // 执行查询
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectSql.toString());
             ResultSet rs = stmt.executeQuery()) {

            // 处理每条记录
            while (rs.next()) {
                // 获取主键值
                Object primaryKeyValue = rs.getObject(primaryKeyField);

                // 构建UPDATE SQL
                StringBuilder updateSql = new StringBuilder("UPDATE ");
                updateSql.append(tableName).append(" SET ");

                List<String> setClauses = new ArrayList<>();
                int fieldProcessed = 0;

                // 遍历需要加密的字段
                for (Map.Entry<String, Class<? extends FieldEncryptorStrategy>> entry : encryptFields.entrySet()) {
                    String fieldName = entry.getKey();
                    Class<? extends FieldEncryptorStrategy> strategyClass = entry.getValue();

                    try {
                        // 获取字段值
                        Object fieldValue = rs.getObject(fieldName);
                        if (fieldValue == null) {
                            continue; // 跳过null值
                        }

                        String originalValue = String.valueOf(fieldValue);

                        // 获取加密策略
                        FieldEncryptorStrategy strategy = StrategyCache.getStrategy(strategyClass);

                        // 加密或解密
                        String processedValue;
                        if (isEncrypt) {
                            processedValue = strategy.encryption(originalValue);
                        } else {
                            processedValue = strategy.decryption(originalValue);
                        }

                        // 构建SET子句
                        String setClause = fieldName + " = '" + processedValue.replace("'", "''") + "'";
                        setClauses.add(setClause);
                        fieldProcessed++;

                    } catch (Exception e) {
                        log.warn("处理字段 {} 失败: {}", fieldName, e.getMessage());
                        // 继续处理其他字段
                    }
                }

                if (!setClauses.isEmpty()) {
                    updateSql.append(String.join(", ", setClauses));
                    updateSql.append(" WHERE ").append(primaryKeyField).append(" = ");

                    // 处理主键值（支持字符串和数字）
                    if (primaryKeyValue instanceof String) {
                        updateSql.append("'").append(primaryKeyValue.toString().replace("'", "''")).append("'");
                    } else {
                        updateSql.append(primaryKeyValue);
                    }

                    String finalSql = updateSql.toString();
                    sqlStatements.add(finalSql);
                    log.debug("生成UPDATE SQL: {}", finalSql);

                    // 执行UPDATE
                    try (PreparedStatement updateStmt = conn.prepareStatement(finalSql)) {
                        int rowsAffected = updateStmt.executeUpdate();
                        if (rowsAffected > 0) {
                            processedCount++;
                            processedFieldCount += fieldProcessed;
                            log.debug("更新成功，影响行数: {}", rowsAffected);
                        }
                    } catch (SQLException e) {
                        log.error("执行UPDATE SQL失败: {}", finalSql, e);
                        throw new SQLException("执行UPDATE SQL失败: " + e.getMessage());
                    }
                }
            }
        }

        response.setProcessedCount(processedCount);
        response.setProcessedFieldCount(processedFieldCount);
        response.setSqlStatements(sqlStatements);

        return response;
    }

    /**
     * 获取配置信息接口
     * 返回 FieldEncryptorProperties 的配置信息（支持多数据源）
     */
    @GetMapping("/api/config.json")
    public ApiResponse<ConfigResponse> getConfig(HttpSession session) {
        // 检查登录
        if (!checkLogin(session)) {
            return ApiResponse.error(401, "未登录");
        }

        try {
            log.debug("获取配置信息 - fieldEncryptorProperties: {}", fieldEncryptorProperties != null ? "已注入" : "未注入");
            
            ConfigResponse response = new ConfigResponse();

            if (fieldEncryptorProperties == null) {
                log.warn("fieldEncryptorProperties 为 null，返回空配置");
                // 返回空配置而不是错误，确保前端能正常显示
                response.setEnable(false);
                response.setFailurePolicy("FALLBACK");
                response.setTables(new ArrayList<>());
                return ApiResponse.success(response);
            }
            
            log.debug("获取配置信息 - enable: {}, failurePolicy: {}, tables size: {}", 
                    fieldEncryptorProperties.isEnable(), 
                    fieldEncryptorProperties.getFailurePolicy(),
                    fieldEncryptorProperties.getTables() != null ? fieldEncryptorProperties.getTables().size() : 0);

            // 设置基本配置
            response.setEnable(fieldEncryptorProperties.isEnable());
            response.setFailurePolicy(fieldEncryptorProperties.getFailurePolicy() != null 
                    ? fieldEncryptorProperties.getFailurePolicy().name() 
                    : "FALLBACK");

            // 设置SQL解析缓存配置
            if (fieldEncryptorProperties.getSqlParseCache() != null) {
                ConfigResponse.SqlParseCacheConfigInfo cacheConfig = new ConfigResponse.SqlParseCacheConfigInfo();
                cacheConfig.setEnable(fieldEncryptorProperties.getSqlParseCache().isEnable());
                cacheConfig.setMaxSize(fieldEncryptorProperties.getSqlParseCache().getMaxSize());
                response.setSqlParseCache(cacheConfig);
            }

            // 设置表配置（支持多数据源）
            List<ConfigResponse.TableConfigInfo> tableConfigs = new ArrayList<>();
            if (fieldEncryptorProperties.getTables() != null && !fieldEncryptorProperties.getTables().isEmpty()) {
                for (FieldEncryptorProperties.TableConfig tableConfig : fieldEncryptorProperties.getTables()) {
                    ConfigResponse.TableConfigInfo tableInfo = new ConfigResponse.TableConfigInfo();
                    tableInfo.setTableName(tableConfig.getTableName());
                    
                    // 多数据源逻辑：设置数据源标识
                    // 如果 datasourceId 为空或空白，表示应用到所有数据源（单数据源场景），设置为 "default"
                    // 如果指定了 datasourceId，则使用指定的值（多数据源场景）
                    String datasourceId = tableConfig.getDatasourceId();
                    tableInfo.setDatasourceId(datasourceId == null || datasourceId.trim().isEmpty() 
                            ? "default" : datasourceId);
                    
                    if (tableConfig.getFields() != null && !tableConfig.getFields().isEmpty()) {
                        List<ConfigResponse.FieldConfigInfo> fieldConfigs = new ArrayList<>();
                        
                        for (FieldEncryptorProperties.FieldConfig fieldConfig : tableConfig.getFields()) {
                            ConfigResponse.FieldConfigInfo fieldInfo = new ConfigResponse.FieldConfigInfo();
                            fieldInfo.setFieldName(fieldConfig.getFieldName());
                            fieldInfo.setStrategy(fieldConfig.getStrategy());
                            fieldConfigs.add(fieldInfo);
                        }
                        
                        tableInfo.setFields(fieldConfigs);
                    }
                    
                    tableConfigs.add(tableInfo);
                }
            }
            
            response.setTables(tableConfigs);

            return ApiResponse.success(response);

        } catch (Exception e) {
            log.error("获取配置信息失败", e);
            return ApiResponse.error("获取配置信息失败: " + e.getMessage());
        }
    }
}

