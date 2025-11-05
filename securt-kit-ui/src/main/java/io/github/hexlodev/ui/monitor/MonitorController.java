package io.github.hexlodev.ui.monitor;

import cn.hutool.core.lang.Pair;
import io.github.hexlodev.core.TableCache;
import io.github.hexlodev.core.cache.StrategyCache;
import io.github.hexlodev.core.parser.SecurtkitUtils;
import io.github.hexlodev.core.parser.dto.ColumnTableDto;
import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
import io.github.hexlodev.ui.monitor.dto.*;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.lang.reflect.Method;
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
@EnableConfigurationProperties(MonitorProperties.class)
public class MonitorController {

    @Autowired
    private MonitorProperties properties;

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
    public ApiResponse<EncryptResponse> encrypt(@RequestBody EncryptRequest request,
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
    public ApiResponse<DecryptResponse> decrypt(@RequestBody DecryptRequest request,
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
            strategyClass = TableCache.getTableFieldEncryptInfo(tableName, fieldName);
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
    public ApiResponse<ParseSqlResponse> parseSql(@RequestBody ParseSqlRequest request,
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
     * SQL 加密接口
     * 将 SQL 中需要加密的字段值进行加密
     */
    @PostMapping("/api/encrypt-sql.json")
    public ApiResponse<EncryptSqlResponse> encryptSql(@RequestBody EncryptSqlRequest request,
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
            log.error("SQL 加密失败", e);
            return ApiResponse.error("SQL 加密失败: " + e.getMessage());
        }
    }

    /**
     * 加密 SQL 中的字段值
     */
    private String encryptSqlValues(String sql) throws JSQLParserException {
        // 解析 SQL
        Statement statement = CCJSqlParserUtil.parse(sql);
        
        if (!(statement instanceof Update)) {
            throw new IllegalArgumentException("当前仅支持 UPDATE 语句");
        }

        Update update = (Update) statement;
        String tableName = update.getTable().getName().toLowerCase();
        
        // 存储需要替换的值：原值 -> 加密后的值
        Map<String, String> valueReplacements = new LinkedHashMap<>();
        
        // 遍历 UPDATE SET 子句
        List<UpdateSet> updateSets = update.getUpdateSets();
        for (UpdateSet updateSet : updateSets) {
            List<Column> columns = updateSet.getColumns();
            ExpressionList<Expression> expressions = (ExpressionList<Expression>) updateSet.getValues();
            
            for (int i = 0; i < columns.size(); i++) {
                Column column = columns.get(i);
                Expression expression = expressions.get(i);
                
                String fieldName = column.getColumnName().toLowerCase();
                
                // 检查该字段是否需要加密
                Class<? extends FieldEncryptorStrategy> strategyClass = 
                    TableCache.getTableFieldEncryptInfo(tableName, fieldName);
                
                if (strategyClass != null) {
                    // 提取表达式的值
                    String originalValue = extractValueFromExpression(expression);
                    if (originalValue != null) {
                        // 加密值
                        FieldEncryptorStrategy strategy = StrategyCache.getStrategy(strategyClass);
                        String encryptedValue = strategy.encryption(originalValue);
                        
                        // 记录替换关系（使用原始SQL中的格式，包括引号）
                        String originalSqlValue = expression.toString();
                        String encryptedSqlValue = formatSqlValue(encryptedValue);
                        
                        valueReplacements.put(originalSqlValue, encryptedSqlValue);
                    }
                }
            }
        }
        
        // 替换 SQL 中的值
        String result = sql;
        for (Map.Entry<String, String> entry : valueReplacements.entrySet()) {
            // 使用正则表达式精确匹配，避免替换部分匹配
            String original = Pattern.quote(entry.getKey());
            String replacement = Matcher.quoteReplacement(entry.getValue());
            result = result.replaceAll(original, replacement);
        }
        
        return result;
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
        if (exprStr.startsWith("'") && exprStr.endsWith("'")) {
            // 去掉引号并处理转义
            return exprStr.substring(1, exprStr.length() - 1).replace("''", "'");
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
}

