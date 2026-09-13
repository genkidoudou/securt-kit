package io.github.genkidoudou.core.parser;

import cn.hutool.core.lang.Pair;
import io.github.genkidoudou.core.cache.StrategyCache;
import io.github.genkidoudou.core.config.ConfigInitializer;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.core.config.TableConfigRegistry;
import io.github.genkidoudou.core.parser.dto.ColumnTableDto;
import io.github.genkidoudou.core.parser.dto.FieldEncryptorInfoDto;
import io.github.genkidoudou.core.parser.dto.ParameterMatchType;
import io.github.genkidoudou.core.strategy.FieldEncryptorStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaygroundScenarioSqlParseProbeTest {

    static class DemoStrategy implements FieldEncryptorStrategy {
        @Override
        public String encryption(String oldValue) {
            return oldValue + "(加密)";
        }

        @Override
        public String decryption(String oldValue) {
            return oldValue == null ? null : oldValue.replace("(加密)", "");
        }
    }

    @BeforeEach
    void setUp() {
        ConfigInitializer.reset();
        TableConfigRegistry.clear();
        StrategyCache.clear();
        DemoStrategy strategy = new DemoStrategy();
        StrategyCache.registerStrategy(FieldEncryptorStrategy.class, strategy);
        StrategyCache.registerStrategy(DemoStrategy.class, strategy);

        FieldEncryptorProperties props = new FieldEncryptorProperties();
        props.setEnable(true);
        FieldEncryptorProperties.TableConfig user = new FieldEncryptorProperties.TableConfig();
        user.setTableName("user");
        FieldEncryptorProperties.FieldConfig phone = new FieldEncryptorProperties.FieldConfig();
        phone.setFieldName("phone");
        phone.setStrategy(DemoStrategy.class.getName());
        FieldEncryptorProperties.FieldConfig name = new FieldEncryptorProperties.FieldConfig();
        name.setFieldName("name");
        name.setStrategy(DemoStrategy.class.getName());
        user.setFields(Arrays.asList(phone, name));

        FieldEncryptorProperties.TableConfig orders = new FieldEncryptorProperties.TableConfig();
        orders.setTableName("orders");
        FieldEncryptorProperties.FieldConfig cPhone = new FieldEncryptorProperties.FieldConfig();
        cPhone.setFieldName("customer_phone");
        cPhone.setStrategy(DemoStrategy.class.getName());
        FieldEncryptorProperties.FieldConfig cName = new FieldEncryptorProperties.FieldConfig();
        cName.setFieldName("customer_name");
        cName.setStrategy(DemoStrategy.class.getName());
        orders.setFields(Arrays.asList(cPhone, cName));
        props.setTables(Arrays.asList(user, orders));
        ConfigInitializer.initialize(props);
    }

    @AfterEach
    void tearDown() {
        ConfigInitializer.reset();
        TableConfigRegistry.clear();
        StrategyCache.clear();
    }

    @Test
    void columnAliasWhereGetsIndexAndResultMapsMobile() throws Exception {
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> r = SecurtkitUtils.parseSql(
                "SELECT id, name, phone AS mobile, age, email FROM \"user\" WHERE phone = ?");
        ColumnTableDto param = firstParam(r.getKey());
        assertEquals(Integer.valueOf(1), param.getInsertFieldIndex());
        assertEquals("user", param.getSourceTableName());
        assertEquals("phone", param.getSourceColumn());
        assertTrue(r.getValue().stream().anyMatch(f ->
                "mobile".equalsIgnoreCase(f.getColumnName()) && "phone".equalsIgnoreCase(f.getSourceColumn())));
    }

    @Test
    void joinSelectMapsUserPhoneAlias() throws Exception {
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> r = SecurtkitUtils.parseSql(
                "SELECT o.id AS orderId, u.name AS userName, u.phone AS userPhone "
                        + "FROM orders o INNER JOIN \"user\" u ON o.user_id = u.id WHERE o.user_id = ?");
        assertTrue(r.getValue().stream().anyMatch(f ->
                "userPhone".equalsIgnoreCase(f.getColumnName()) && "phone".equalsIgnoreCase(f.getSourceColumn())),
                "fields=" + r.getValue());
        assertTrue(r.getValue().stream().anyMatch(f ->
                "userName".equalsIgnoreCase(f.getColumnName()) && "name".equalsIgnoreCase(f.getSourceColumn())),
                "fields=" + r.getValue());
    }

    @Test
    void upperWrappedPlaceholderGetsIndex() throws Exception {
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> r = SecurtkitUtils.parseSql(
                "SELECT id, phone FROM \"user\" WHERE UPPER(phone) = UPPER(?)");
        ColumnTableDto param = firstParam(r.getKey());
        assertEquals(Integer.valueOf(1), param.getInsertFieldIndex(),
                "wrapped placeholder must still resolve insertFieldIndex");
        assertEquals("user", param.getSourceTableName());
        assertEquals("phone", param.getSourceColumn());
    }

    @Test
    void likePlaceholderGetsLikeMatchType() throws Exception {
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> r = SecurtkitUtils.parseSql(
                "SELECT id, phone FROM \"user\" WHERE phone LIKE ?");
        ColumnTableDto param = firstParam(r.getKey());
        assertEquals(Integer.valueOf(1), param.getInsertFieldIndex());
        assertEquals(ParameterMatchType.LIKE, param.getMatchType());
    }

    private static ColumnTableDto firstParam(Map<String, ColumnTableDto> map) {
        assertNotNull(map);
        assertTrue(!map.isEmpty());
        return map.values().iterator().next();
    }
}
