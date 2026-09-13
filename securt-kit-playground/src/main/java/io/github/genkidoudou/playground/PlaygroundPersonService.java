package io.github.genkidoudou.playground;

import io.github.genkidoudou.core.config.ConfigInitializer;
import io.github.genkidoudou.core.config.DigestConfigRegistry;
import io.github.genkidoudou.core.config.EncryptModeHolder;
import io.github.genkidoudou.core.config.TableConfigRegistry;
import io.github.genkidoudou.core.crypto.FieldCryptoServiceHolder;
import io.github.genkidoudou.core.digest.DigestService;
import io.github.genkidoudou.core.digest.ResolvedDigestRule;
import io.github.genkidoudou.core.strategy.FieldEncryptorStrategy;
import io.github.genkidoudou.playground.dto.ApiResponse;
import io.github.genkidoudou.playground.dto.PersonPreflight;
import io.github.genkidoudou.playground.dto.PersonRequest;
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
 * Fixed-table person maintenance for Playground UI.
 */
@Slf4j
public class PlaygroundPersonService {

    static final String TABLE = "playground_person";
    private static final String VIEW_BUSINESS = "business";
    private static final String VIEW_RAW = "raw";
    private static final List<String> SELECT_COLUMNS =
            Arrays.asList("id", "name", "phone", "id_card", "age", "row_digest");

    private final PlaygroundProperties properties;
    private final DataSource primaryDataSource;
    private final PlaygroundDataSourceLocator locator;
    private final DigestService digestService = new DigestService();

    public PlaygroundPersonService(PlaygroundProperties properties,
                                   DataSource primaryDataSource,
                                   PlaygroundDataSourceLocator locator) {
        this.properties = properties != null ? properties : new PlaygroundProperties();
        this.primaryDataSource = primaryDataSource;
        this.locator = locator;
    }

    public ApiResponse<PersonPreflight> preflight(String datasourceId) {
        PersonPreflight result = new PersonPreflight();
        List<PersonPreflight.Check> checks = result.getChecks();
        DataSource ds = null;
        String resolvedId = null;
        try {
            ResolvedDataSource resolved = resolve(datasourceId);
            ds = resolved.dataSource;
            resolvedId = resolved.id;
            checks.add(new PersonPreflight.Check("datasource", true, "数据源可用: " + resolvedId));
        } catch (IllegalArgumentException e) {
            checks.add(new PersonPreflight.Check("datasource", false, e.getMessage()));
        }
        result.setDatasourceId(resolvedId != null ? resolvedId : datasourceId);

        boolean allowed = isAllowedTable(TABLE);
        checks.add(new PersonPreflight.Check("allowed-table", allowed,
                allowed ? "playground_person 已加入白名单" : "playground_person 不在 allowed-tables"));

        Set<String> columns = ds == null ? Collections.<String>emptySet() : readColumns(ds);
        for (String required : SELECT_COLUMNS) {
            boolean present = columns.contains(required);
            checks.add(new PersonPreflight.Check("column-" + required, present,
                    present ? "字段存在: " + required : "缺少字段: " + required));
        }

        Map<String, Class<? extends FieldEncryptorStrategy>> encrypted =
                TableConfigRegistry.getTableFields(TABLE, resolvedId);
        boolean phoneEncrypted = encrypted != null && encrypted.containsKey("phone");
        boolean idCardEncrypted = encrypted != null && encrypted.containsKey("id_card");
        checks.add(new PersonPreflight.Check("encrypted-phone", phoneEncrypted,
                phoneEncrypted ? "phone 已配置加密" : "phone 未配置加密"));
        checks.add(new PersonPreflight.Check("encrypted-id_card", idCardEncrypted,
                idCardEncrypted ? "id_card 已配置加密" : "id_card 未配置加密"));

        ResolvedDigestRule digestRule = findDigestRule(resolvedId);
        checks.add(new PersonPreflight.Check("person-digest", digestRule != null,
                digestRule != null ? "phone+id_card -> row_digest 摘要规则可用"
                        : "缺少 phone+id_card -> row_digest 摘要规则"));

        String modeLabel = EncryptModeHolder.getMode() == null
                ? "UNKNOWN" : EncryptModeHolder.getMode().name();
        checks.add(new PersonPreflight.Check("encrypt-mode-info", true,
                "当前加密模式: " + modeLabel + "（人员台使用显式加解密，不依赖 JDBC 拦截）"));
        boolean skip = ConfigInitializer.shouldSkipByComment("/* SECURT_SKIP */ SELECT 1");
        checks.add(new PersonPreflight.Check("skip-comment", skip,
                skip ? "SECURT_SKIP 已启用" : "SECURT_SKIP 未启用或 token 不匹配"));

        boolean ready = true;
        for (PersonPreflight.Check check : checks) {
            if ("encrypt-mode-info".equals(check.getId())) {
                continue;
            }
            ready &= check.isPassed();
        }
        result.setReady(ready);
        return ApiResponse.success(result, ready ? "人员表已就绪" : "人员表前置检查未通过");
    }

    public ApiResponse<Map<String, Object>> create(PersonRequest request) {
        String invalid = validateWrite(request, false);
        if (invalid != null) {
            return ApiResponse.error(400, invalid);
        }
        try {
            ResolvedDataSource resolved = resolve(request.getDatasourceId());
            String name = request.getName().trim();
            String phonePlain = request.getPhone().trim();
            String idCardPlain = request.getIdCard().trim();
            String phoneEnc = encryptField(resolved.id, "phone", phonePlain);
            String idCardEnc = encryptField(resolved.id, "id_card", idCardPlain);
            String digest = computeRowDigest(resolved.id, phonePlain, idCardPlain, null, null, null);
            String sql = "/* SECURT_SKIP */ INSERT INTO playground_person "
                    + "(name, phone, id_card, age, row_digest) VALUES (?, ?, ?, ?, ?)";
            Long id;
            try (Connection conn = resolved.dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, phoneEnc);
                ps.setString(3, idCardEnc);
                ps.setObject(4, request.getAge());
                ps.setString(5, digest);
                ps.executeUpdate();
                id = generatedKey(ps);
            }
            if (id == null) {
                return ApiResponse.error(500, "新增成功但未返回记录 ID");
            }
            Map<String, Object> row = loadBusinessRow(resolved.dataSource, resolved.id, id);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", id);
            data.put("row", row);
            return ApiResponse.success(data, "新增成功");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.warn("Create playground_person failed", e);
            return ApiResponse.error(500, "新增失败: " + safeMessage(e));
        }
    }

    public ApiResponse<Map<String, Object>> update(PersonRequest request) {
        String invalid = validateWrite(request, true);
        if (invalid != null) {
            return ApiResponse.error(400, invalid);
        }
        try {
            ResolvedDataSource resolved = resolve(request.getDatasourceId());
            if (loadRawRow(resolved.dataSource, request.getId()).isEmpty()) {
                return ApiResponse.error(404, "记录不存在: " + request.getId());
            }
            String name = request.getName().trim();
            String phonePlain = request.getPhone().trim();
            String idCardPlain = request.getIdCard().trim();
            String phoneEnc = encryptField(resolved.id, "phone", phonePlain);
            String idCardEnc = encryptField(resolved.id, "id_card", idCardPlain);
            String digest = computeRowDigest(resolved.id, phonePlain, idCardPlain,
                    resolved.dataSource, "id = ?", Collections.<Object>singletonList(request.getId()));
            String sql = "/* SECURT_SKIP */ UPDATE playground_person SET name = ?, phone = ?, "
                    + "id_card = ?, age = ?, row_digest = ? WHERE id = ?";
            try (Connection conn = resolved.dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, phoneEnc);
                ps.setString(3, idCardEnc);
                ps.setObject(4, request.getAge());
                ps.setString(5, digest);
                ps.setLong(6, request.getId());
                ps.executeUpdate();
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", request.getId());
            data.put("row", loadBusinessRow(resolved.dataSource, resolved.id, request.getId()));
            return ApiResponse.success(data, "修改成功");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.warn("Update playground_person failed", e);
            return ApiResponse.error(500, "修改失败: " + safeMessage(e));
        }
    }

    public ApiResponse<Map<String, Object>> delete(PersonRequest request) {
        if (request == null || request.getId() == null) {
            return ApiResponse.error(400, "id 不能为空");
        }
        try {
            ResolvedDataSource resolved = resolve(request.getDatasourceId());
            if (loadRawRow(resolved.dataSource, request.getId()).isEmpty()) {
                return ApiResponse.error(404, "记录不存在: " + request.getId());
            }
            try (Connection conn = resolved.dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM playground_person WHERE id = ?")) {
                ps.setLong(1, request.getId());
                ps.executeUpdate();
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", request.getId());
            return ApiResponse.success(data, "删除成功");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.warn("Delete playground_person failed", e);
            return ApiResponse.error(500, "删除失败: " + safeMessage(e));
        }
    }

    public ApiResponse<Map<String, Object>> list(PersonRequest request) {
        if (request == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        String view = normalizeView(request.getView());
        if (view == null) {
            return ApiResponse.error(400, "view 必须是 business 或 raw");
        }
        try {
            ResolvedDataSource resolved = resolve(request.getDatasourceId());
            List<Object> values = new ArrayList<>();
            StringBuilder where = new StringBuilder();
            appendEquality(where, values, "name", request.getName());
            appendEncryptedEquality(where, values, resolved.id, "phone", request.getPhone());
            appendEncryptedEquality(where, values, resolved.id, "id_card", request.getIdCard());

            String selectList = "id, name, phone, id_card, age, row_digest";
            // 业务视图也走 skip + 显式解密，避免依赖 JDBC 拦截 / MYBATIS 插件
            String sql = "/* SECURT_SKIP */ SELECT " + selectList + " FROM playground_person";
            if (where.length() > 0) {
                sql += " WHERE " + where;
            }
            sql += " ORDER BY id DESC";

            List<Map<String, Object>> rows = new ArrayList<>();
            try (Connection conn = resolved.dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < values.size(); i++) {
                    ps.setObject(i + 1, values.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = mapRow(rs);
                        if (VIEW_BUSINESS.equals(view)) {
                            decryptSensitive(row, resolved.id);
                        }
                        rows.add(row);
                    }
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("view", view);
            data.put("rows", rows);
            data.put("total", rows.size());
            return ApiResponse.success(data, "查询成功");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.warn("List playground_person failed", e);
            return ApiResponse.error(500, "查询失败: " + safeMessage(e));
        }
    }

    private void appendEquality(StringBuilder where, List<Object> values, String column, String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return;
        }
        if (where.length() > 0) {
            where.append(" AND ");
        }
        where.append(column).append(" = ?");
        values.add(raw.trim());
    }

    private void appendEncryptedEquality(StringBuilder where, List<Object> values,
                                         String datasourceId, String column, String plain) {
        if (plain == null || plain.trim().isEmpty()) {
            return;
        }
        if (where.length() > 0) {
            where.append(" AND ");
        }
        where.append(column).append(" = ?");
        values.add(encryptField(datasourceId, column, plain.trim()));
    }

    private String validateWrite(PersonRequest request, boolean requireId) {
        if (request == null) {
            return "请求体不能为空";
        }
        if (requireId && request.getId() == null) {
            return "id 不能为空";
        }
        if (blank(request.getName())) {
            return "姓名不能为空";
        }
        if (blank(request.getPhone())) {
            return "手机号不能为空";
        }
        if (blank(request.getIdCard())) {
            return "身份证号不能为空";
        }
        if (request.getAge() != null && request.getAge() < 0) {
            return "年龄不能为负数";
        }
        return null;
    }

    private String normalizeView(String view) {
        if (view == null || view.trim().isEmpty()) {
            return VIEW_BUSINESS;
        }
        String normalized = view.trim().toLowerCase(Locale.ROOT);
        if (VIEW_BUSINESS.equals(normalized) || VIEW_RAW.equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private Map<String, Object> loadRawRow(DataSource ds, Long id) throws Exception {
        return loadRow(ds, "/* SECURT_SKIP */ SELECT id, name, phone, id_card, age, row_digest "
                + "FROM playground_person WHERE id = ?", id, null, false);
    }

    private Map<String, Object> loadBusinessRow(DataSource ds, String datasourceId, Long id) throws Exception {
        return loadRow(ds, "/* SECURT_SKIP */ SELECT id, name, phone, id_card, age, row_digest "
                + "FROM playground_person WHERE id = ?", id, datasourceId, true);
    }

    private Map<String, Object> loadRow(DataSource ds, String sql, Long id,
                                        String datasourceId, boolean decrypt) throws Exception {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Collections.emptyMap();
                }
                Map<String, Object> row = mapRow(rs);
                if (decrypt) {
                    decryptSensitive(row, datasourceId);
                }
                return row;
            }
        }
    }

    private String encryptField(String datasourceId, String column, String plain) {
        return FieldCryptoServiceHolder.get().encrypt(TABLE, column, plain, datasourceId);
    }

    private void decryptSensitive(Map<String, Object> row, String datasourceId) {
        if (row == null || row.isEmpty()) {
            return;
        }
        Object phone = row.get("phone");
        if (phone != null) {
            row.put("phone", FieldCryptoServiceHolder.get()
                    .decrypt(TABLE, "phone", String.valueOf(phone), datasourceId));
        }
        Object idCard = row.get("idCard");
        if (idCard != null) {
            row.put("idCard", FieldCryptoServiceHolder.get()
                    .decrypt(TABLE, "id_card", String.valueOf(idCard), datasourceId));
        }
    }

    private String computeRowDigest(String datasourceId,
                                    String phonePlain,
                                    String idCardPlain,
                                    DataSource ds,
                                    String reloadWhereSql,
                                    List<Object> reloadWhereParams) throws Exception {
        Map<String, String> plain = new LinkedHashMap<>();
        plain.put("phone", phonePlain);
        plain.put("id_card", idCardPlain);
        if (ds == null) {
            Map<String, String> digests = digestService.computeTargetDigests(
                    TABLE, datasourceId, plain, true, null, null, null);
            return digests.get("row_digest");
        }
        try (Connection conn = ds.getConnection()) {
            Map<String, String> digests = digestService.computeTargetDigests(
                    TABLE, datasourceId, plain, false, conn, reloadWhereSql, reloadWhereParams);
            return digests.get("row_digest");
        }
    }

    private Map<String, Object> mapRow(ResultSet rs) throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String label = normalize(meta.getColumnLabel(i));
            Object value = rs.getObject(i);
            if ("id_card".equals(label)) {
                row.put("idCard", value);
            } else {
                row.put(label, value);
            }
        }
        return row;
    }

    private Long generatedKey(PreparedStatement ps) throws Exception {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getLong(1);
            }
        }
        return null;
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
            log.debug("Unable to inspect person table", e);
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
            if (!"row_digest".equals(normalize(rule.getTargetField()))) {
                continue;
            }
            if (containsIgnoreCase(rule.getSourceFields(), "phone")
                    && containsIgnoreCase(rule.getSourceFields(), "id_card")) {
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

    private boolean isAllowedTable(String table) {
        List<String> allowed = properties.getAllowedTables();
        if (allowed == null) {
            return false;
        }
        for (String item : allowed) {
            if (table.equalsIgnoreCase(normalize(item))) {
                return true;
            }
        }
        return false;
    }

    private ResolvedDataSource resolve(String requestedId) {
        Map<String, DataSource> all = resolveAll();
        if (all.isEmpty()) {
            throw new IllegalArgumentException("未配置可用数据源");
        }
        String id = requestedId == null || requestedId.trim().isEmpty()
                ? defaultDatasourceId(all) : requestedId.trim();
        DataSource ds = all.get(id);
        if (ds == null) {
            throw new IllegalArgumentException("未知数据源: " + id);
        }
        return new ResolvedDataSource(id, ds);
    }

    private Map<String, DataSource> resolveAll() {
        Map<String, DataSource> map = new LinkedHashMap<>();
        if (locator != null) {
            Map<String, DataSource> located = locator.locate();
            if (located != null) {
                map.putAll(located);
            }
        }
        if (map.isEmpty() && primaryDataSource != null) {
            map.put("default", primaryDataSource);
        }
        return map;
    }

    private String defaultDatasourceId(Map<String, DataSource> map) {
        if (map.containsKey("primary")) {
            return "primary";
        }
        if (map.containsKey("default")) {
            return "default";
        }
        return map.keySet().iterator().next();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
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
