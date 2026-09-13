package io.github.genkidoudou.playground;

import io.github.genkidoudou.core.config.ConfigInitializer;
import io.github.genkidoudou.core.config.DigestConfigRegistry;
import io.github.genkidoudou.core.config.EncryptModeHolder;
import io.github.genkidoudou.core.config.TableConfigRegistry;
import io.github.genkidoudou.core.digest.ResolvedDigestRule;
import io.github.genkidoudou.core.strategy.FieldEncryptorStrategy;
import io.github.genkidoudou.playground.dto.ApiResponse;
import io.github.genkidoudou.playground.dto.LifecyclePreflight;
import io.github.genkidoudou.playground.dto.LifecycleRequest;
import io.github.genkidoudou.playground.dto.LifecycleSnapshot;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Stateless, fixed-table encryption lifecycle used by the Playground UI.
 */
@Slf4j
public class PlaygroundLifecycleService {

    private static final String TABLE = "digest_user";
    private static final Set<String> BUSINESS_FIELDS =
            new LinkedHashSet<>(Arrays.asList("name", "phone", "age", "email"));
    private static final Set<String> TAMPER_TARGETS =
            new LinkedHashSet<>(Arrays.asList("row_digest", "phone"));

    private final PlaygroundProperties properties;
    private final DataSource primaryDataSource;
    private final PlaygroundDataSourceLocator locator;

    public PlaygroundLifecycleService(PlaygroundProperties properties,
                                      DataSource primaryDataSource,
                                      PlaygroundDataSourceLocator locator) {
        this.properties = properties != null ? properties : new PlaygroundProperties();
        this.primaryDataSource = primaryDataSource;
        this.locator = locator;
    }

    public ApiResponse<LifecyclePreflight> preflight(String datasourceId) {
        LifecyclePreflight result = new LifecyclePreflight();
        List<LifecyclePreflight.Check> checks = result.getChecks();
        DataSource ds = null;
        String resolvedId = null;
        try {
            ResolvedDataSource resolved = resolve(datasourceId);
            ds = resolved.dataSource;
            resolvedId = resolved.id;
            addCheck(checks, "datasource", true, "数据源可用: " + resolvedId);
        } catch (IllegalArgumentException e) {
            addCheck(checks, "datasource", false, e.getMessage());
        }
        result.setDatasourceId(resolvedId != null ? resolvedId : datasourceId);

        boolean allowed = isAllowedTable(TABLE);
        addCheck(checks, "allowed-table", allowed,
                allowed ? "digest_user 已加入白名单" : "digest_user 不在 allowed-tables");

        Set<String> columns = ds == null ? Collections.<String>emptySet() : readColumns(ds);
        for (String required : Arrays.asList("id", "name", "phone", "age", "email", "row_digest")) {
            boolean present = columns.contains(required);
            addCheck(checks, "column-" + required, present,
                    present ? "字段存在: " + required : "缺少字段: " + required);
        }

        Map<String, Class<? extends FieldEncryptorStrategy>> encrypted =
                TableConfigRegistry.getTableFields(TABLE, resolvedId);
        boolean nameEncrypted = encrypted != null && encrypted.containsKey("name");
        boolean phoneEncrypted = encrypted != null && encrypted.containsKey("phone");
        addCheck(checks, "encrypted-name", nameEncrypted,
                nameEncrypted ? "name 已配置加密" : "name 未配置加密");
        addCheck(checks, "encrypted-phone", phoneEncrypted,
                phoneEncrypted ? "phone 已配置加密" : "phone 未配置加密");

        ResolvedDigestRule digestRule = findDigestRule(resolvedId);
        addCheck(checks, "phone-digest", digestRule != null,
                digestRule != null ? "phone -> row_digest 摘要规则可用" : "缺少 phone -> row_digest 摘要规则");

        boolean jdbc = EncryptModeHolder.isJdbc();
        addCheck(checks, "jdbc-mode", jdbc,
                jdbc ? "JDBC 拦截模式已启用" : "生命周期演示要求 JDBC 拦截模式");
        boolean skip = ConfigInitializer.shouldSkipByComment("/* SECURT_SKIP */ SELECT 1");
        addCheck(checks, "skip-comment", skip,
                skip ? "SECURT_SKIP 已启用" : "SECURT_SKIP 未启用或 token 不匹配");

        boolean ready = true;
        for (LifecyclePreflight.Check check : checks) {
            ready &= check.isPassed();
        }
        result.setReady(ready);
        return ApiResponse.success(result, ready ? "生命周期已就绪" : "生命周期前置检查未通过");
    }

    public ApiResponse<LifecycleSnapshot> insert(LifecycleRequest request) {
        String invalid = validateInsert(request);
        if (invalid != null) {
            return ApiResponse.error(400, invalid);
        }
        try {
            ResolvedDataSource resolved = resolve(request.getDatasourceId());
            String sql = "INSERT INTO digest_user (name, phone, age, email) VALUES (?, ?, ?, ?)";
            Long id;
            try (Connection conn = resolved.dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, request.getName());
                ps.setString(2, request.getPhone());
                ps.setObject(3, request.getAge());
                ps.setString(4, request.getEmail());
                ps.executeUpdate();
                id = generatedKey(ps);
            }
            if (id == null) {
                return ApiResponse.error(500, "新增成功但未返回记录 ID");
            }
            Map<String, Object> plaintext = directPlaintext(request);
            LifecycleSnapshot snapshot = collectSnapshot("insert", id, plaintext, resolved.dataSource);
            assertDifferent(snapshot, "name-encrypted", plaintext.get("name"),
                    snapshot.getRawDatabaseRow().get("name"), "name 已加密", "未观察到 name 加密效果");
            assertDifferent(snapshot, "phone-encrypted", plaintext.get("phone"),
                    snapshot.getRawDatabaseRow().get("phone"), "phone 已加密", "未观察到 phone 加密效果");
            assertPresent(snapshot, "digest-generated", snapshot.getRawDatabaseRow().get("row_digest"),
                    "摘要已生成", "row_digest 为空");
            assertEqualsValue(snapshot, "business-name", plaintext.get("name"),
                    snapshot.getBusinessRow().get("name"), "name 解密正确");
            assertEqualsValue(snapshot, "business-phone", plaintext.get("phone"),
                    snapshot.getBusinessRow().get("phone"), "phone 解密正确");
            finish(snapshot, "新增成功，加密与摘要证据已采集");
            return ApiResponse.success(snapshot, snapshot.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return failure("insert", request == null ? null : request.getRecordId(), e);
        }
    }

    public ApiResponse<LifecycleSnapshot> query(LifecycleRequest request) {
        return readAndVerify("query", request, false);
    }

    public ApiResponse<LifecycleSnapshot> verify(LifecycleRequest request) {
        return readAndVerify("verify", request, true);
    }

    public ApiResponse<LifecycleSnapshot> update(LifecycleRequest request) {
        String invalid = validateRecordRequest(request);
        if (invalid != null) {
            return ApiResponse.error(400, invalid);
        }
        Map<String, Object> fields;
        try {
            fields = updateFields(request);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
        if (fields.isEmpty()) {
            return ApiResponse.error(400, "至少提供一个可修改业务字段");
        }
        try {
            ResolvedDataSource resolved = resolve(request.getDatasourceId());
            Map<String, Object> before = loadRawRow(resolved.dataSource, request.getRecordId());
            if (before.isEmpty()) {
                return ApiResponse.error(404, "记录不存在: " + request.getRecordId());
            }
            StringBuilder sql = new StringBuilder("UPDATE digest_user SET ");
            List<Object> values = new ArrayList<>();
            boolean first = true;
            for (Map.Entry<String, Object> field : fields.entrySet()) {
                if (!first) {
                    sql.append(", ");
                }
                first = false;
                sql.append(field.getKey()).append(" = ?");
                values.add(field.getValue());
            }
            sql.append(" WHERE id = ?");
            values.add(request.getRecordId());
            try (Connection conn = resolved.dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                bind(ps, values);
                ps.executeUpdate();
            }
            LifecycleSnapshot snapshot = collectSnapshot(
                    "update", request.getRecordId(), fields, resolved.dataSource);
            for (Map.Entry<String, Object> field : fields.entrySet()) {
                assertEqualsValue(snapshot, "business-" + field.getKey(), field.getValue(),
                        snapshot.getBusinessRow().get(field.getKey()), field.getKey() + " 更新后业务值正确");
            }
            if (fields.containsKey("phone")) {
                assertDifferent(snapshot, "phone-cipher-changed", before.get("phone"),
                        snapshot.getRawDatabaseRow().get("phone"), "phone 密文已变化", "phone 密文未变化");
                assertDifferent(snapshot, "digest-changed", before.get("row_digest"),
                        snapshot.getRawDatabaseRow().get("row_digest"), "摘要已重新生成", "摘要未随 phone 更新");
            }
            finish(snapshot, "修改完成，已采集更新后的保护证据");
            return ApiResponse.success(snapshot, snapshot.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return failure("update", request.getRecordId(), e);
        }
    }

    public ApiResponse<LifecycleSnapshot> tamper(LifecycleRequest request) {
        String invalid = validateRecordRequest(request);
        if (invalid != null) {
            return ApiResponse.error(400, invalid);
        }
        String target = normalize(request.getTamperTarget());
        if (!TAMPER_TARGETS.contains(target)) {
            return ApiResponse.error(400, "不支持的篡改目标: " + request.getTamperTarget());
        }
        try {
            ResolvedDataSource resolved = resolve(request.getDatasourceId());
            if (loadRawRow(resolved.dataSource, request.getRecordId()).isEmpty()) {
                return ApiResponse.error(404, "记录不存在: " + request.getRecordId());
            }
            String value = "TAMPERED_" + target + "_" + System.nanoTime();
            String sql = "/* SECURT_SKIP */ UPDATE digest_user SET " + target + " = ? WHERE id = ?";
            try (Connection conn = resolved.dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, value);
                ps.setLong(2, request.getRecordId());
                ps.executeUpdate();
            }
            LifecycleSnapshot snapshot = newSnapshot("tamper", request.getRecordId());
            snapshot.setRawDatabaseRow(loadRawRow(resolved.dataSource, request.getRecordId()));
            try {
                snapshot.setBusinessRow(loadBusinessRow(resolved.dataSource, request.getRecordId()));
                addAssertion(snapshot, "tamper-detected", false, "正常读取触发完整性失败",
                        "正常读取成功", "篡改未被检测");
                snapshot.setDigestVerification(digest("FAILED", "篡改未触发摘要失败策略"));
            } catch (Exception verificationFailure) {
                addAssertion(snapshot, "tamper-detected", true, "正常读取触发完整性失败",
                        verificationFailure.getClass().getSimpleName(), "已检测到篡改");
                snapshot.setDigestVerification(digest("FAILED", verificationFailure.getMessage()));
                snapshot.setError(new LifecycleSnapshot.ErrorDetail(
                        "DIGEST_VERIFICATION", safeMessage(verificationFailure)));
            }
            boolean detected = snapshot.getAssertions().get(0).isPassed();
            snapshot.setStatus(detected ? "PASSED" : "FAILED");
            snapshot.setMessage(detected ? "篡改已被完整性校验检测" : "篡改验证失败");
            return ApiResponse.success(snapshot, snapshot.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return failure("tamper", request.getRecordId(), e);
        }
    }

    private ApiResponse<LifecycleSnapshot> readAndVerify(String step,
                                                         LifecycleRequest request,
                                                         boolean digestStep) {
        String invalid = validateRecordRequest(request);
        if (invalid != null) {
            return ApiResponse.error(400, invalid);
        }
        try {
            ResolvedDataSource resolved = resolve(request.getDatasourceId());
            Map<String, Object> raw = loadRawRow(resolved.dataSource, request.getRecordId());
            if (raw.isEmpty()) {
                return ApiResponse.error(404, "记录不存在: " + request.getRecordId());
            }
            LifecycleSnapshot snapshot = newSnapshot(step, request.getRecordId());
            snapshot.setRawDatabaseRow(raw);
            try {
                snapshot.setBusinessRow(loadBusinessRow(resolved.dataSource, request.getRecordId()));
                snapshot.setDigestVerification(digest("PASSED", "正常读取未触发摘要校验异常"));
                addAssertion(snapshot, digestStep ? "digest-valid" : "business-read",
                        true, "正常读取成功", "正常读取成功",
                        digestStep ? "摘要校验通过" : "查询并解密成功");
            } catch (Exception verificationFailure) {
                snapshot.setDigestVerification(digest("FAILED", safeMessage(verificationFailure)));
                snapshot.setError(new LifecycleSnapshot.ErrorDetail(
                        "DIGEST_VERIFICATION", safeMessage(verificationFailure)));
                addAssertion(snapshot, digestStep ? "digest-valid" : "business-read",
                        false, "正常读取成功", verificationFailure.getClass().getSimpleName(),
                        digestStep ? "摘要校验失败" : "业务读取失败");
            }
            finish(snapshot, digestStep ? "摘要校验完成" : "查询完成");
            return ApiResponse.success(snapshot, snapshot.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return failure(step, request.getRecordId(), e);
        }
    }

    private LifecycleSnapshot collectSnapshot(String step,
                                              Long id,
                                              Map<String, Object> plaintext,
                                              DataSource ds) throws Exception {
        LifecycleSnapshot snapshot = newSnapshot(step, id);
        snapshot.setRequestPlaintext(new LinkedHashMap<>(plaintext));
        snapshot.setRawDatabaseRow(loadRawRow(ds, id));
        try {
            snapshot.setBusinessRow(loadBusinessRow(ds, id));
            snapshot.setDigestVerification(digest("PASSED", "正常读取未触发摘要校验异常"));
        } catch (Exception e) {
            snapshot.setDigestVerification(digest("FAILED", safeMessage(e)));
            snapshot.setError(new LifecycleSnapshot.ErrorDetail("DIGEST_VERIFICATION", safeMessage(e)));
        }
        return snapshot;
    }

    private Map<String, Object> loadRawRow(DataSource ds, Long id) throws Exception {
        return loadRow(ds, "/* SECURT_SKIP */ SELECT id, name, phone, age, email, row_digest "
                + "FROM digest_user WHERE id = ?", id);
    }

    private Map<String, Object> loadBusinessRow(DataSource ds, Long id) throws Exception {
        return loadRow(ds, "SELECT id, name, phone, age, email, row_digest "
                + "FROM digest_user WHERE id = ?", id);
    }

    private Map<String, Object> loadRow(DataSource ds, String sql, Long id) throws Exception {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Collections.emptyMap();
                }
                Map<String, Object> row = new LinkedHashMap<>();
                ResultSetMetaData meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    String label = meta.getColumnLabel(i);
                    row.put(normalize(label), rs.getObject(i));
                }
                return row;
            }
        }
    }

    private Set<String> readColumns(DataSource ds) {
        Set<String> columns = new LinkedHashSet<>();
        try (Connection conn = ds.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            readColumns(meta, TABLE, columns);
            if (columns.isEmpty()) {
                readColumns(meta, TABLE.toUpperCase(Locale.ROOT), columns);
            }
        } catch (Exception e) {
            log.debug("Unable to inspect lifecycle table", e);
        }
        return columns;
    }

    private void readColumns(DatabaseMetaData meta, String table, Set<String> columns) throws Exception {
        try (ResultSet rs = meta.getColumns(null, null, table, null)) {
            while (rs.next()) {
                columns.add(normalize(rs.getString("COLUMN_NAME")));
            }
        }
    }

    private ResolvedDigestRule findDigestRule(String datasourceId) {
        for (ResolvedDigestRule rule : DigestConfigRegistry.getRules(TABLE, datasourceId)) {
            if ("row_digest".equals(normalize(rule.getTargetField()))
                    && containsIgnoreCase(rule.getSourceFields(), "phone")) {
                return rule;
            }
        }
        return null;
    }

    private boolean containsIgnoreCase(List<String> values, String expected) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (expected.equals(normalize(value))) {
                return true;
            }
        }
        return false;
    }

    private String validateInsert(LifecycleRequest request) {
        if (request == null) {
            return "请求体不能为空";
        }
        if (request.getTable() != null && !request.getTable().trim().isEmpty()) {
            return "生命周期接口不接受客户端表名";
        }
        String invalidField = invalidField(request.getFields());
        if (invalidField != null) {
            return invalidField;
        }
        if (blank(request.getName()) || blank(request.getPhone())) {
            return "name 和 phone 不能为空";
        }
        return null;
    }

    private String validateRecordRequest(LifecycleRequest request) {
        if (request == null || request.getRecordId() == null) {
            return "recordId 不能为空";
        }
        if (request.getTable() != null && !request.getTable().trim().isEmpty()) {
            return "生命周期接口不接受客户端表名";
        }
        return null;
    }

    private String invalidField(Map<String, Object> fields) {
        if (fields == null) {
            return null;
        }
        for (String field : fields.keySet()) {
            if (!BUSINESS_FIELDS.contains(normalize(field))) {
                return "不允许的生命周期字段: " + field;
            }
        }
        return null;
    }

    private Map<String, Object> updateFields(LifecycleRequest request) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (request.getFields() != null && !request.getFields().isEmpty()) {
            String invalid = invalidField(request.getFields());
            if (invalid != null) {
                throw new IllegalArgumentException(invalid);
            }
            for (Map.Entry<String, Object> field : request.getFields().entrySet()) {
                fields.put(normalize(field.getKey()), field.getValue());
            }
            return fields;
        }
        if (request.getName() != null) fields.put("name", request.getName());
        if (request.getPhone() != null) fields.put("phone", request.getPhone());
        if (request.getAge() != null) fields.put("age", request.getAge());
        if (request.getEmail() != null) fields.put("email", request.getEmail());
        return fields;
    }

    private Map<String, Object> directPlaintext(LifecycleRequest request) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", request.getName());
        values.put("phone", request.getPhone());
        values.put("age", request.getAge());
        values.put("email", request.getEmail());
        return values;
    }

    private boolean isAllowedTable(String table) {
        if (properties.getAllowedTables() == null) {
            return false;
        }
        for (String allowed : properties.getAllowedTables()) {
            if (table.equals(normalize(allowed))) {
                return true;
            }
        }
        return false;
    }

    private ResolvedDataSource resolve(String requestedId) {
        Map<String, DataSource> all = new LinkedHashMap<>();
        if (locator != null && locator.locate() != null) {
            all.putAll(locator.locate());
        }
        if (all.isEmpty() && primaryDataSource != null) {
            all.put("default", primaryDataSource);
        }
        if (all.isEmpty()) {
            throw new IllegalArgumentException("未配置 DataSource");
        }
        String id = requestedId == null || requestedId.trim().isEmpty()
                ? defaultDatasourceId(all) : requestedId.trim();
        DataSource ds = all.get(id);
        if (ds == null) {
            throw new IllegalArgumentException("未知数据源: " + id + "，可选: " + all.keySet());
        }
        return new ResolvedDataSource(id, ds);
    }

    private String defaultDatasourceId(Map<String, DataSource> all) {
        if (all.containsKey("primary")) return "primary";
        if (all.containsKey("default")) return "default";
        return all.keySet().iterator().next();
    }

    private Long generatedKey(PreparedStatement ps) throws Exception {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            return keys.next() ? keys.getLong(1) : null;
        }
    }

    private void bind(PreparedStatement ps, List<Object> values) throws Exception {
        for (int i = 0; i < values.size(); i++) {
            ps.setObject(i + 1, values.get(i));
        }
    }

    private LifecycleSnapshot newSnapshot(String step, Long id) {
        LifecycleSnapshot snapshot = new LifecycleSnapshot();
        snapshot.setStep(step);
        snapshot.setRecordId(id);
        return snapshot;
    }

    private LifecycleSnapshot.DigestVerification digest(String status, String detail) {
        return new LifecycleSnapshot.DigestVerification(
                status, Collections.singletonList("phone"), "row_digest", detail);
    }

    private void assertDifferent(LifecycleSnapshot snapshot,
                                 String id,
                                 Object expectedNotEqual,
                                 Object actual,
                                 String success,
                                 String failure) {
        boolean passed = expectedNotEqual != null && actual != null
                && !String.valueOf(expectedNotEqual).equals(String.valueOf(actual));
        addAssertion(snapshot, id, passed, "值应不同",
                summarize(actual), passed ? success : failure);
    }

    private void assertPresent(LifecycleSnapshot snapshot,
                               String id,
                               Object actual,
                               String success,
                               String failure) {
        boolean passed = actual != null && !String.valueOf(actual).trim().isEmpty();
        addAssertion(snapshot, id, passed, "非空", summarize(actual), passed ? success : failure);
    }

    private void assertEqualsValue(LifecycleSnapshot snapshot,
                                   String id,
                                   Object expected,
                                   Object actual,
                                   String success) {
        boolean passed = expected == null ? actual == null : String.valueOf(expected).equals(String.valueOf(actual));
        addAssertion(snapshot, id, passed, summarize(expected), summarize(actual),
                passed ? success : success + "（实际不一致）");
    }

    private void addAssertion(LifecycleSnapshot snapshot,
                              String id,
                              boolean passed,
                              String expected,
                              String actual,
                              String message) {
        snapshot.getAssertions().add(
                new LifecycleSnapshot.Assertion(id, passed, expected, actual, message));
    }

    private void finish(LifecycleSnapshot snapshot, String successMessage) {
        boolean passed = snapshot.getError() == null;
        for (LifecycleSnapshot.Assertion assertion : snapshot.getAssertions()) {
            passed &= assertion.isPassed();
        }
        snapshot.setStatus(passed ? "PASSED" : "FAILED");
        snapshot.setMessage(passed ? successMessage : firstFailure(snapshot));
    }

    private String firstFailure(LifecycleSnapshot snapshot) {
        for (LifecycleSnapshot.Assertion assertion : snapshot.getAssertions()) {
            if (!assertion.isPassed()) {
                return assertion.getMessage();
            }
        }
        return snapshot.getError() != null ? snapshot.getError().getDetail() : "生命周期断言失败";
    }

    private ApiResponse<LifecycleSnapshot> failure(String step, Long id, Exception e) {
        log.warn("Playground lifecycle {} failed", step, e);
        LifecycleSnapshot snapshot = newSnapshot(step, id);
        snapshot.setStatus("FAILED");
        snapshot.setMessage(safeMessage(e));
        snapshot.setError(new LifecycleSnapshot.ErrorDetail(
                "DATABASE_OPERATION", safeMessage(e)));
        return ApiResponse.success(snapshot, snapshot.getMessage());
    }

    private void addCheck(List<LifecyclePreflight.Check> checks,
                          String id,
                          boolean passed,
                          String message) {
        checks.add(new LifecyclePreflight.Check(id, passed, message));
    }

    private String summarize(Object value) {
        if (value == null) return "null";
        String text = String.valueOf(value);
        return text.length() > 80 ? text.substring(0, 77) + "..." : text;
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class ResolvedDataSource {
        private final String id;
        private final DataSource dataSource;

        private ResolvedDataSource(String id, DataSource dataSource) {
            this.id = id;
            this.dataSource = dataSource;
        }
    }
}
