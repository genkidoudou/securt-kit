package io.github.hexlodev.core.parser;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import io.github.hexlodev.core.TableCache;
import io.github.hexlodev.core.config.FieldEncryptorProperties;
import io.github.hexlodev.core.parser.dto.ColumnTableDto;
import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
import io.github.hexlodev.core.parser.visitor.PoJoEncrtptorStatementVisitor;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 策略解析与 SQL 解析入口
 * @author luyanan
 * @since 2025/10/27
 */

public class SecurtkitUtils {
    /**
     * 占位符前缀
     */
    public static final String PLACEHOLDER = "SECURT_KIT_PLACEHOLDER_";
    private static final AtomicInteger PLACEHOLDER_COUNTER = new AtomicInteger(0);

    /**
     * 解析sql,获取入参和响应对应的表字段关系
     *
     * @param sql
     * @return
     * @since 2025/10/10
     */
    public static Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseSql(String sql) throws JSQLParserException {
        //1.将sql中的 ? 占位符替换成我们自定义的特殊符号
        String placeholderSql = question2Placeholder(sql);
        //2.解析sql的响应结果，和占位符对应的表字段关系
        Statement statement = CCJSqlParserUtil.parse(placeholderSql);
        PoJoEncrtptorStatementVisitor poJoEncrtptorStatementVisitor = new PoJoEncrtptorStatementVisitor();
        statement.accept(poJoEncrtptorStatementVisitor);

        //3.获取解析结果
        Map<String, ColumnTableDto> placeholderColumnTableMap = poJoEncrtptorStatementVisitor.getPlaceholderColumnTableMap();
        for (Map.Entry<String, ColumnTableDto> entry : placeholderColumnTableMap.entrySet()) {
            String key = entry.getKey();
            ColumnTableDto value = entry.getValue();

            if (key.startsWith("SECURT_KIT_PLACEHOLDER_")) {
                Integer index = Integer.parseInt(key.replace("SECURT_KIT_PLACEHOLDER_", ""));
                value.setInsertFieldIndex(index);
            }
        }
//        for (Map.Entry<String, ColumnTableDto> entry : placeholderColumnTableMap.entrySet()) {
//            ColumnTableDto columnTableDto = entry.getValue();
//            ParameterMapping parameterMapping = parameterMappings.get(columnTableDto.getInsertFieldIndex());
//            String property = parameterMapping.getProperty();
//
//            columnTableDto.setParameterProperty(property);
//        }
        List<FieldEncryptorInfoDto> fieldEncryptorInfos = poJoEncrtptorStatementVisitor.getFieldEncryptorInfos();
        return Pair.of(placeholderColumnTableMap, fieldEncryptorInfos);
    }

    /**
     * 将SQL中的问号占位符替换为自定义占位符
     */
    public static String question2Placeholder(String sql) {
        if (StrUtil.isBlank(sql)) {
            return sql;
        }

        // 重置计数器
        PLACEHOLDER_COUNTER.set(0);

        // 使用正则表达式替换问号，但要避免替换字符串字面量中的问号
        Pattern pattern = Pattern.compile("\\?");
        Matcher matcher = pattern.matcher(sql);

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String replacement = PLACEHOLDER + PLACEHOLDER_COUNTER.getAndIncrement();
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    public static void main(String[] args) {
        //  UPDATE SQL
        String sql = "UPDATE user SET name = ?, age = ?,phone= ?  WHERE id = ?";
        try {
            FieldEncryptorProperties fieldEncryptorProperties = new FieldEncryptorProperties();
            fieldEncryptorProperties.setEnable(true);
            FieldEncryptorProperties.TableConfig tableConfig = new FieldEncryptorProperties.TableConfig();
            tableConfig.setTableName("user");
            FieldEncryptorProperties.FieldConfig name_fieldConfig = new FieldEncryptorProperties.FieldConfig();
            name_fieldConfig.setFieldName("name");

            FieldEncryptorProperties.FieldConfig phone_fieldConfig = new FieldEncryptorProperties.FieldConfig();
            name_fieldConfig.setFieldName("phone");
            tableConfig.setFields(CollectionUtil.newArrayList(name_fieldConfig, phone_fieldConfig));
            fieldEncryptorProperties.setTables(CollectionUtil.newArrayList(tableConfig));
            TableCache.init(fieldEncryptorProperties);
            Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair = parseSql(sql);
            System.out.println(pair);
        } catch (JSQLParserException e) {
            throw new RuntimeException(e);
        }
    }
}
