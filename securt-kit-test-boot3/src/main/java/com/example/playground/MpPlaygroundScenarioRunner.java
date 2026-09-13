package com.example.playground;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.entity.UserEntity;
import com.example.mapper.PlaygroundScenarioMapper;
import com.example.mapper.UserEntityMapper;
import io.github.genkidoudou.core.TableCache;
import io.github.genkidoudou.core.config.EncryptModeHolder;
import io.github.genkidoudou.core.config.TableConfigRegistry;
import io.github.genkidoudou.core.crypto.FieldCryptoServiceHolder;
import io.github.genkidoudou.core.strategy.FieldEncryptorStrategy;
import io.github.genkidoudou.core.strategy.like.LikePatternHandlerHolder;
import io.github.genkidoudou.playground.PlaygroundScenarioCatalog;
import io.github.genkidoudou.playground.PlaygroundScenarioRunner;
import io.github.genkidoudou.playground.dto.ScenarioDescriptor;
import io.github.genkidoudou.playground.dto.ScenarioRunResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Boot3 MyBatis-Plus complex-query scenario runner for Playground.
 * Simple scenarios use BaseMapper/Wrapper; complex scenarios use PlaygroundScenarioMapper XML.
 */
public class MpPlaygroundScenarioRunner implements PlaygroundScenarioRunner {

    private static final String SAMPLE_PHONE = "13800138000";

    private final UserEntityMapper userMapper;
    private final PlaygroundScenarioMapper scenarioMapper;
    private final DataSource dataSource;

    public MpPlaygroundScenarioRunner(UserEntityMapper userMapper,
                                      PlaygroundScenarioMapper scenarioMapper,
                                      DataSource dataSource) {
        this.userMapper = userMapper;
        this.scenarioMapper = scenarioMapper;
        this.dataSource = dataSource;
    }

    @Override
    public List<ScenarioDescriptor> list() {
        List<ScenarioDescriptor> catalog = PlaygroundScenarioCatalog.baseline();
        enrichSamples(catalog);
        String reason = availabilityReason();
        if (reason != null) {
            for (ScenarioDescriptor d : catalog) {
                d.unavailable(reason);
            }
        }
        return catalog;
    }

    private void enrichSamples(List<ScenarioDescriptor> catalog) {
        String cipherPhone = encryptSamplePhone();
        for (ScenarioDescriptor d : catalog) {
            String id = d.getId() == null ? "" : d.getId();
            Map<String, Object> samples = new LinkedHashMap<>();
            switch (id) {
                case "single-eq":
                    samples.put("phone", SAMPLE_PHONE);
                    d.setSampleParams(samples);
                    d.setExampleSqlPlain(
                            "SELECT id, name, phone, age, email FROM \"user\" WHERE phone = '"
                                    + SAMPLE_PHONE + "' LIMIT 50");
                    d.setExampleSqlCipher(
                            "/* SECURT_SKIP */ SELECT id, name, phone, age, email FROM \"user\" "
                                    + "WHERE phone = '" + escapeSqlLiteral(cipherPhone) + "' LIMIT 50");
                    d.setSampleHint("种子 user.phone 明文演示值 " + SAMPLE_PHONE + "（库内密文由策略生成）");
                    break;
                case "join-user-orders":
                    samples.put("userId", "1");
                    d.setSampleParams(samples);
                    d.setExampleSqlPlain(
                            "SELECT o.id, o.order_no, u.name, u.phone FROM orders o "
                                    + "INNER JOIN \"user\" u ON o.user_id = u.id WHERE o.user_id = 1 LIMIT 50");
                    d.setExampleSqlCipher(
                            "/* SECURT_SKIP */ SELECT o.id, o.order_no, u.name, u.phone FROM orders o "
                                    + "INNER JOIN \"user\" u ON o.user_id = u.id WHERE o.user_id = 1 LIMIT 50");
                    d.setSampleHint("种子用户 id=1 与 orders.user_id 关联");
                    break;
                case "column-alias":
                    samples.put("phone", SAMPLE_PHONE);
                    d.setSampleParams(samples);
                    d.setExampleSqlPlain(
                            "SELECT id, name, phone AS mobile, age, email FROM \"user\" "
                                    + "WHERE phone = '" + SAMPLE_PHONE + "' LIMIT 50");
                    d.setExampleSqlCipher(
                            "/* SECURT_SKIP */ SELECT id, name, phone AS mobile, age, email FROM \"user\" "
                                    + "WHERE phone = '" + escapeSqlLiteral(cipherPhone) + "' LIMIT 50");
                    d.setSampleHint("列别名 mobile 对应加密列 phone");
                    break;
                case "table-alias":
                    samples.put("phone", SAMPLE_PHONE);
                    d.setSampleParams(samples);
                    d.setExampleSqlPlain(
                            "SELECT u.id, u.name, u.phone FROM \"user\" u WHERE u.phone = '"
                                    + SAMPLE_PHONE + "' LIMIT 50");
                    d.setExampleSqlCipher(
                            "/* SECURT_SKIP */ SELECT u.id, u.name, u.phone FROM \"user\" u "
                                    + "WHERE u.phone = '" + escapeSqlLiteral(cipherPhone) + "' LIMIT 50");
                    d.setSampleHint("H2 需对保留字表名加双引号：\"user\"");
                    break;
                case "like-phone":
                    samples.put("pattern", SAMPLE_PHONE);
                    d.setSampleParams(samples);
                    d.setExampleSqlPlain(
                            "SELECT id, name, phone FROM \"user\" WHERE phone LIKE '"
                                    + SAMPLE_PHONE + "' LIMIT 50");
                    d.setExampleSqlCipher(
                            "/* SECURT_SKIP */ SELECT id, name, phone FROM \"user\" "
                                    + "WHERE phone LIKE '" + escapeSqlLiteral(cipherPhone) + "' LIMIT 50");
                    d.setSampleHint("默认 ExactMatch：无通配符时按密文精确匹配");
                    break;
                case "func-on-cipher":
                    samples.put("phone", SAMPLE_PHONE);
                    d.setSampleParams(samples);
                    d.setExampleSqlPlain(
                            "SELECT id, name, phone FROM \"user\" WHERE UPPER(phone) = UPPER('"
                                    + SAMPLE_PHONE + "') LIMIT 50");
                    d.setExampleSqlCipher(
                            "/* SECURT_SKIP */ SELECT id, name, phone FROM \"user\" "
                                    + "WHERE UPPER(phone) = UPPER('" + SAMPLE_PHONE + "') LIMIT 50");
                    d.setSampleHint("函数作用在密文列上不具备明文语义，允许 0 行");
                    break;
                default:
                    break;
            }
        }
    }

    private String encryptSamplePhone() {
        try {
            return FieldCryptoServiceHolder.get().encrypt("user", "phone", SAMPLE_PHONE, null);
        } catch (Exception e) {
            return SAMPLE_PHONE;
        }
    }

    private static String escapeSqlLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    @Override
    public ScenarioRunResult run(String scenarioId, Map<String, Object> params, String datasourceId) {
        String reason = availabilityReason();
        if (reason != null) {
            throw new IllegalArgumentException(reason);
        }
        String id = scenarioId == null ? "" : scenarioId.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> p = params == null ? new LinkedHashMap<String, Object>() : params;
        switch (id) {
            case "single-eq":
                return runSingleEq(p);
            case "join-user-orders":
                return runJoin(p);
            case "like-phone":
                return runLikePhone(p);
            case "column-alias":
                return runColumnAlias(p);
            case "table-alias":
                return runTableAlias(p);
            case "func-on-cipher":
                return runFuncOnCipher(p);
            default:
                throw new IllegalArgumentException("未知场景: " + scenarioId);
        }
    }

    private ScenarioRunResult runSingleEq(Map<String, Object> params) {
        String phone = required(params, "phone");
        QueryWrapper<UserEntity> qw = new QueryWrapper<>();
        qw.eq("phone", phone).last("LIMIT 50");
        List<UserEntity> entities = userMapper.selectList(qw);
        List<Map<String, Object>> plain = mapUsers(entities);
        String cipherPhone = FieldCryptoServiceHolder.get().encrypt("user", "phone", phone, null);
        List<Map<String, Object>> cipher = querySkip(
                "/* SECURT_SKIP */ SELECT id, name, phone, age, email FROM \"user\" WHERE phone = ? LIMIT 50",
                cipherPhone);
        return result("single-eq", plain, cipher, meta(
                "UserEntityMapper BaseMapper + QueryWrapper.eq(\"phone\", plainPhone)",
                "密文栏使用 SECURT_SKIP + 显式加密后的 phone 等值条件",
                null));
    }

    private ScenarioRunResult runJoin(Map<String, Object> params) {
        Long userId = asLong(params.get("userId"));
        String phone = asString(params.get("phone"));
        List<Map<String, Object>> plain = normalizeKeys(scenarioMapper.selectJoinUserOrders(userId, phone));
        StringBuilder sql = new StringBuilder(
                "/* SECURT_SKIP */ SELECT o.id AS orderId, o.order_no AS orderNo, o.customer_name AS customerName, "
                        + "o.customer_phone AS customerPhone, o.user_id AS userId, u.name AS userName, "
                        + "u.phone AS userPhone "
                        + "FROM orders o INNER JOIN \"user\" u ON o.user_id = u.id WHERE 1=1");
        List<Object> bind = new ArrayList<>();
        if (userId != null) {
            sql.append(" AND o.user_id = ?");
            bind.add(userId);
        }
        if (phone != null && !phone.isEmpty()) {
            sql.append(" AND u.phone = ?");
            bind.add(FieldCryptoServiceHolder.get().encrypt("user", "phone", phone, null));
        }
        sql.append(" LIMIT 50");
        List<Map<String, Object>> cipher = querySkip(sql.toString(), bind.toArray());
        return result("join-user-orders", plain, cipher, meta(
                "PlaygroundScenarioMapper.selectJoinUserOrders (XML JOIN)",
                "密文栏: SECURT_SKIP JOIN SQL + 可选显式加密 phone",
                null));
    }

    private ScenarioRunResult runLikePhone(Map<String, Object> params) {
        String pattern = required(params, "pattern");
        // 勿用 QueryWrapper.like：MP 会自动包成 %pattern%，触发 ExactMatchLikePatternHandler SKIP
        List<Map<String, Object>> plain = normalizeKeys(scenarioMapper.selectByPhoneLike(pattern));
        String handler = LikePatternHandlerHolder.getHandler().getClass().getSimpleName();
        String cipherPattern = FieldCryptoServiceHolder.get().encrypt("user", "phone",
                stripWildcards(pattern), null);
        List<Map<String, Object>> cipher = querySkip(
                "/* SECURT_SKIP */ SELECT id, name, phone, age, email FROM \"user\" WHERE phone LIKE ? LIMIT 50",
                pattern.contains("%") || pattern.contains("_") ? pattern : cipherPattern);
        Map<String, Object> sqlMeta = meta(
                "PlaygroundScenarioMapper.selectByPhoneLike (XML phone LIKE #{pattern})",
                "LikePatternHandler=" + handler
                        + "：默认 ExactMatchLikePatternHandler 仅对无 %/_ 的模式加密后做精确匹配；"
                        + "含通配符时跳过加密，密文列 LIKE 通常无法按明文语义命中",
                null);
        sqlMeta.put("likeHandler", handler);
        return result("like-phone", plain, cipher, sqlMeta);
    }

    private ScenarioRunResult runColumnAlias(Map<String, Object> params) {
        String phone = required(params, "phone");
        List<Map<String, Object>> plain = normalizeKeys(scenarioMapper.selectByPhoneColumnAlias(phone));
        String enc = FieldCryptoServiceHolder.get().encrypt("user", "phone", phone, null);
        List<Map<String, Object>> cipher = querySkip(
                "/* SECURT_SKIP */ SELECT id, name, phone AS mobile, age, email FROM \"user\" WHERE phone = ? LIMIT 50",
                enc);
        return result("column-alias", plain, cipher, meta(
                "PlaygroundScenarioMapper.selectByPhoneColumnAlias (XML phone AS mobile)",
                "别名 mobile 对应加密列 phone；明文栏经 EncryptInterceptor 解密",
                null));
    }

    private ScenarioRunResult runTableAlias(Map<String, Object> params) {
        String phone = required(params, "phone");
        List<Map<String, Object>> plain = normalizeKeys(scenarioMapper.selectByPhoneTableAlias(phone));
        String enc = FieldCryptoServiceHolder.get().encrypt("user", "phone", phone, null);
        List<Map<String, Object>> cipher = querySkip(
                "/* SECURT_SKIP */ SELECT u.id, u.name, u.phone, u.age, u.email FROM \"user\" u WHERE u.phone = ? LIMIT 50",
                enc);
        return result("table-alias", plain, cipher, meta(
                "PlaygroundScenarioMapper.selectByPhoneTableAlias (XML table alias u)",
                "密文栏: SECURT_SKIP + 显式加密 phone",
                null));
    }

    private ScenarioRunResult runFuncOnCipher(Map<String, Object> params) {
        String phone = asString(params.get("phone"));
        if (phone == null || phone.isEmpty()) {
            phone = SAMPLE_PHONE;
        }
        List<Map<String, Object>> plain = normalizeKeys(scenarioMapper.selectFuncOnCipher(phone));
        List<Map<String, Object>> cipher = querySkip(
                "/* SECURT_SKIP */ SELECT id, name, phone, age, email FROM \"user\" WHERE UPPER(phone) = UPPER(?) LIMIT 50",
                phone);
        String limitation = "加密列上的 SQL 函数（如 UPPER）不具备明文语义保证；允许 0 行成功，仅作能力边界演示";
        return result("func-on-cipher", plain, cipher, meta(
                "PlaygroundScenarioMapper.selectFuncOnCipher (XML UPPER(phone))",
                "对比 XML 明文路径与 SECURT_SKIP；结果行数可能为 0",
                limitation));
    }

    private String availabilityReason() {
        Map<String, Class<? extends FieldEncryptorStrategy>> userFields =
                TableConfigRegistry.getTableFields("user", null);
        if (userFields == null || !userFields.containsKey("phone")) {
            userFields = TableCache.getTableFieldEncryptInfo("user", null);
        }
        if (userFields == null || !userFields.containsKey("phone")) {
            return "user.phone 未配置加密，复杂查询场景不可用";
        }
        return null;
    }

    private ScenarioRunResult result(String id,
                                     List<Map<String, Object>> plain,
                                     List<Map<String, Object>> cipher,
                                     Map<String, Object> sqlMeta) {
        ScenarioRunResult result = new ScenarioRunResult();
        result.setScenarioId(id);
        result.setPlainRows(plain);
        result.setCipherRows(cipher);
        result.setSqlMeta(sqlMeta);
        return result;
    }

    private Map<String, Object> meta(String sql, String explanation, String limitation) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("encryptMode", EncryptModeHolder.getMode() == null
                ? "UNKNOWN" : EncryptModeHolder.getMode().name());
        m.put("sql", sql);
        m.put("explanation", explanation);
        if (limitation != null) {
            m.put("limitation", limitation);
        }
        return m;
    }

    private List<Map<String, Object>> mapUsers(List<UserEntity> entities) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (entities == null) {
            return rows;
        }
        for (UserEntity u : entities) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("name", u.getName());
            row.put("phone", u.getPhone());
            row.put("age", u.getAge());
            row.put("email", u.getEmail());
            rows.add(row);
        }
        return rows;
    }

    /**
     * H2/MyBatis map keys may be uppercased; normalize common labels for UI/tests.
     */
    private List<Map<String, Object>> normalizeKeys(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> row : rows) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : row.entrySet()) {
                String key = e.getKey();
                if (key == null) {
                    continue;
                }
                String lower = key.toLowerCase(Locale.ROOT);
                if ("userid".equals(lower)) {
                    copy.put("userId", e.getValue());
                } else if ("orderid".equals(lower)) {
                    copy.put("orderId", e.getValue());
                } else if ("orderno".equals(lower)) {
                    copy.put("orderNo", e.getValue());
                } else if ("customername".equals(lower)) {
                    copy.put("customerName", e.getValue());
                } else if ("customerphone".equals(lower)) {
                    copy.put("customerPhone", e.getValue());
                } else if ("username".equals(lower)) {
                    copy.put("userName", e.getValue());
                } else if ("userphone".equals(lower)) {
                    copy.put("userPhone", e.getValue());
                } else {
                    copy.put(lower, e.getValue());
                }
            }
            out.add(copy);
        }
        return out;
    }

    private List<Map<String, Object>> querySkip(String sql, Object... binds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < binds.length; i++) {
                ps.setObject(i + 1, binds[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int count = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= count; i++) {
                        String label = meta.getColumnLabel(i);
                        if (label == null || label.isEmpty()) {
                            label = meta.getColumnName(i);
                        }
                        row.put(label, rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("场景旁路查询失败: " + e.getMessage(), e);
        }
        return rows;
    }

    private static String required(Map<String, Object> params, String key) {
        String value = asString(params.get(key));
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("缺少参数: " + key);
        }
        return value;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static Long asLong(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        return Long.valueOf(String.valueOf(value).trim());
    }

    private static String stripWildcards(String pattern) {
        return pattern.replace("%", "").replace("_", "");
    }
}
