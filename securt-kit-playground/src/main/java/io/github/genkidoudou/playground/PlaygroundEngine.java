package io.github.genkidoudou.playground;

import io.github.genkidoudou.core.config.ConfigInitializer;
import io.github.genkidoudou.core.config.EncryptModeHolder;
import io.github.genkidoudou.playground.dto.ApiResponse;
import io.github.genkidoudou.playground.dto.ComplexRunRequest;
import io.github.genkidoudou.playground.dto.CrudRequest;
import io.github.genkidoudou.playground.dto.DigestDemoRequest;
import io.github.genkidoudou.playground.dto.LifecyclePreflight;
import io.github.genkidoudou.playground.dto.LifecycleRequest;
import io.github.genkidoudou.playground.dto.LifecycleSnapshot;
import io.github.genkidoudou.playground.dto.PersonPreflight;
import io.github.genkidoudou.playground.dto.PersonRequest;
import io.github.genkidoudou.playground.dto.ScenarioDescriptor;
import io.github.genkidoudou.playground.dto.ScenarioRunRequest;
import io.github.genkidoudou.playground.dto.ScenarioRunResult;
import io.github.genkidoudou.playground.dto.ScenarioSqlRunRequest;
import io.github.genkidoudou.playground.dto.ScenarioSqlRunResult;
import io.github.genkidoudou.playground.dto.SeedPreviewResult;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Playground 业务引擎：表白名单 + CRUD / Digest / Complex
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Slf4j
public class PlaygroundEngine {

    private static final String DIGEST_TABLE = "digest_user";
    private static final String SKIP_COMMENT_PREFIX = "/* SECURT_SKIP */ ";
    private static final int PREVIEW_DEFAULT_LIMIT = 50;
    private static final int PREVIEW_HARD_CAP = 100;
    private static final Set<String> SAFE_COLUMNS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private static final String[] WRITE_OR_DDL_HEADS = {
            "INSERT", "UPDATE", "DELETE", "MERGE", "CREATE", "DROP", "ALTER",
            "TRUNCATE", "REPLACE", "CALL", "EXEC", "EXECUTE", "GRANT", "REVOKE"
    };

    static {
        Collections.addAll(SAFE_COLUMNS,
                "id", "name", "phone", "id_card", "age", "email", "row_digest",
                "user_id", "order_no", "orderno", "customer_name", "customer_phone",
                "amount", "status", "address", "created_time", "updated_time");
    }

    private final PlaygroundProperties properties;
    private final DataSource primaryDataSource;
    private final PlaygroundDataSourceLocator locator;
    private final PlaygroundLifecycleService lifecycleService;
    private final PlaygroundPersonService personService;
    private final PlaygroundScenarioRunner scenarioRunner;

    public PlaygroundEngine(PlaygroundProperties properties,
                            DataSource primaryDataSource,
                            PlaygroundDataSourceLocator locator) {
        this(properties, primaryDataSource, locator, null);
    }

    public PlaygroundEngine(PlaygroundProperties properties,
                            DataSource primaryDataSource,
                            PlaygroundDataSourceLocator locator,
                            PlaygroundScenarioRunner scenarioRunner) {
        this.properties = properties != null ? properties : new PlaygroundProperties();
        this.primaryDataSource = primaryDataSource;
        this.locator = locator;
        this.lifecycleService = new PlaygroundLifecycleService(this.properties, primaryDataSource, locator);
        this.personService = new PlaygroundPersonService(this.properties, primaryDataSource, locator);
        this.scenarioRunner = scenarioRunner;
    }

    public ApiResponse<PersonPreflight> personPreflight(String datasourceId) {
        return personService.preflight(datasourceId);
    }

    public ApiResponse<Map<String, Object>> personList(PersonRequest request) {
        return personService.list(request);
    }

    public ApiResponse<Map<String, Object>> personCreate(PersonRequest request) {
        return personService.create(request);
    }

    public ApiResponse<Map<String, Object>> personUpdate(PersonRequest request) {
        return personService.update(request);
    }

    public ApiResponse<Map<String, Object>> personDelete(PersonRequest request) {
        return personService.delete(request);
    }

    public ApiResponse<List<Map<String, Object>>> listScenarios() {
        List<ScenarioDescriptor> catalog;
        if (scenarioRunner == null) {
            catalog = PlaygroundScenarioCatalog.baseline();
            for (ScenarioDescriptor d : catalog) {
                d.unavailable("当前工程未注册 PlaygroundScenarioRunner（复杂查询需在测试工程启用 MyBatis-Plus 场景实现）");
            }
        } else {
            List<ScenarioDescriptor> fromRunner = scenarioRunner.list();
            catalog = fromRunner != null ? fromRunner : PlaygroundScenarioCatalog.baseline();
        }
        List<Map<String, Object>> payload = new ArrayList<>();
        for (ScenarioDescriptor d : catalog) {
            payload.add(d.toMap());
        }
        return ApiResponse.success(payload, scenarioRunner == null ? "场景目录（不可用）" : "场景目录");
    }

    public ApiResponse<Map<String, Object>> runScenario(ScenarioRunRequest request) {
        if (request == null || request.getScenarioId() == null || request.getScenarioId().trim().isEmpty()) {
            return ApiResponse.error(400, "scenarioId 不能为空");
        }
        if (scenarioRunner == null) {
            return ApiResponse.error(503, "复杂查询不可用：未注册 PlaygroundScenarioRunner");
        }
        try {
            ScenarioRunResult result = scenarioRunner.run(
                    request.getScenarioId().trim(),
                    request.getParams() == null ? Collections.<String, Object>emptyMap() : request.getParams(),
                    request.getDatasourceId());
            if (result == null) {
                return ApiResponse.error(500, "场景执行返回空结果");
            }
            if (result.getScenarioId() == null) {
                result.setScenarioId(request.getScenarioId().trim());
            }
            return ApiResponse.success(result.toMap(), "场景执行完成");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (UnsupportedOperationException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.warn("scenario run failed: {}", request.getScenarioId(), e);
            return ApiResponse.error(500, "场景执行失败: " + e.getMessage());
        }
    }

    public ApiResponse<Map<String, Object>> seedPreview(String datasourceId) {
        try {
            requireAllowedTable("user");
            String orderTable = resolveOrderTable();
            DataSource ds = resolve(datasourceId);
            int limit = PREVIEW_DEFAULT_LIMIT;
            boolean skipEnabled = isSkipCommentEnabled();
            String userSql = buildLimitedSelect(quoteTable("user"), limit, skipEnabled);
            String ordersSql = buildLimitedSelect(quoteTable(orderTable), limit, skipEnabled);
            SeedPreviewResult preview = new SeedPreviewResult();
            preview.setUserRows(queryRows(ds, userSql));
            preview.setOrdersRows(queryRows(ds, ordersSql));
            preview.setEncryptNote(skipEnabled
                    ? "phone / customer_phone 等配置字段库内为密文；本预览经 SECURT_SKIP 读取，展示库内原值"
                    : "skip-comment 未启用：预览可能被解密或与库内原值不一致，写密文条件前请先开启 SECURT_SKIP");
            Map<String, Object> limits = new LinkedHashMap<>();
            limits.put("default", PREVIEW_DEFAULT_LIMIT);
            limits.put("hardCap", PREVIEW_HARD_CAP);
            limits.put("applied", limit);
            preview.setLimits(limits);
            return ApiResponse.success(preview.toMap(), "种子预览");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.warn("seed preview failed", e);
            return ApiResponse.error(500, "种子预览失败: " + e.getMessage());
        }
    }

    public ApiResponse<Map<String, Object>> sqlRun(ScenarioSqlRunRequest request) {
        try {
            if (request == null || request.getSql() == null || request.getSql().trim().isEmpty()) {
                return ApiResponse.error(400, "SQL 不能为空");
            }
            String sql = request.getSql().trim();
            validateSelectOnly(sql);
            boolean useSkip = request.isUseSkip();
            boolean skipped = false;
            if (useSkip) {
                if (!isSkipCommentEnabled()) {
                    return ApiResponse.error(400, "SECURT_SKIP 未启用：请先配置 securtkit.encryptor.skip-comment.enable=true");
                }
                if (!ConfigInitializer.shouldSkipByComment(sql)) {
                    sql = SKIP_COMMENT_PREFIX + sql;
                }
                skipped = true;
            } else {
                skipped = ConfigInitializer.shouldSkipByComment(sql);
            }
            DataSource ds = resolve(request.getDatasourceId());
            List<Map<String, Object>> allRows = queryRows(ds, sql);
            boolean truncated = allRows.size() > PREVIEW_HARD_CAP;
            List<Map<String, Object>> rows = truncated
                    ? new ArrayList<>(allRows.subList(0, PREVIEW_HARD_CAP))
                    : allRows;
            ScenarioSqlRunResult result = new ScenarioSqlRunResult();
            result.setRows(rows);
            Map<String, Object> sqlMeta = new LinkedHashMap<>();
            sqlMeta.put("sql", sql);
            sqlMeta.put("encryptMode", EncryptModeHolder.getMode().name());
            sqlMeta.put("skipped", skipped);
            sqlMeta.put("truncated", truncated);
            sqlMeta.put("rowCount", rows.size());
            if (EncryptModeHolder.isMybatis() && !skipped) {
                sqlMeta.put("note",
                        "当前为 MYBATIS 模式：「执行 SQL」走原始 JDBC，明文字面量不会经 EncryptInterceptor 加密，"
                                + "通常无法命中密文列。请改用「运行场景」，或勾选 SECURT_SKIP 并使用密文示例 SQL。");
            }
            result.setSqlMeta(sqlMeta);
            return ApiResponse.success(result.toMap(), "SQL 执行完成");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.warn("sql run failed", e);
            return ApiResponse.error(500, "SQL 执行失败: " + e.getMessage());
        }
    }

    static void validateSelectOnly(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        if (sql.indexOf(';') >= 0) {
            throw new IllegalArgumentException("仅允许单条 SELECT，禁止分号或多语句");
        }
        String head = stripLeadingComments(sql).trim();
        if (head.isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        String upper = head.toUpperCase(Locale.ROOT);
        for (String banned : WRITE_OR_DDL_HEADS) {
            if (upper.equals(banned) || upper.startsWith(banned + " ") || upper.startsWith(banned + "\t")
                    || upper.startsWith(banned + "\n") || upper.startsWith(banned + "(")) {
                throw new IllegalArgumentException("仅允许只读 SELECT，禁止写操作或 DDL：" + banned);
            }
        }
        if (!upper.startsWith("SELECT")
                || (upper.length() > 6 && Character.isLetterOrDigit(upper.charAt(6)))) {
            throw new IllegalArgumentException("去掉前导注释后必须以 SELECT 开头");
        }
    }

    static String stripLeadingComments(String sql) {
        String s = sql;
        while (true) {
            String trimmed = s.trim();
            if (trimmed.startsWith("--")) {
                int nl = trimmed.indexOf('\n');
                if (nl < 0) {
                    return "";
                }
                s = trimmed.substring(nl + 1);
                continue;
            }
            if (trimmed.startsWith("/*")) {
                int end = trimmed.indexOf("*/");
                if (end < 0) {
                    return "";
                }
                s = trimmed.substring(end + 2);
                continue;
            }
            return trimmed;
        }
    }

    private static boolean isSkipCommentEnabled() {
        return ConfigInitializer.shouldSkipByComment(SKIP_COMMENT_PREFIX + "SELECT 1");
    }

    private static String buildLimitedSelect(String quotedTable, int limit, boolean withSkip) {
        String body = "SELECT * FROM " + quotedTable + " ORDER BY id ASC LIMIT " + Math.min(limit, PREVIEW_HARD_CAP);
        return withSkip ? SKIP_COMMENT_PREFIX + body : body;
    }

    private List<Map<String, Object>> queryRows(DataSource ds, String sql) throws Exception {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return mapResultSet(rs);
        }
    }

    /** @deprecated lifecycle UI removed; retained for transitional tests only */
    public ApiResponse<LifecyclePreflight> lifecyclePreflight(String datasourceId) {
        return lifecycleService.preflight(datasourceId);
    }

    /** @deprecated lifecycle UI removed; retained for transitional tests only */
    public ApiResponse<LifecycleSnapshot> lifecycleInsert(LifecycleRequest request) {
        return lifecycleService.insert(request);
    }

    /** @deprecated lifecycle UI removed; retained for transitional tests only */
    public ApiResponse<LifecycleSnapshot> lifecycleQuery(LifecycleRequest request) {
        return lifecycleService.query(request);
    }

    /** @deprecated lifecycle UI removed; retained for transitional tests only */
    public ApiResponse<LifecycleSnapshot> lifecycleUpdate(LifecycleRequest request) {
        return lifecycleService.update(request);
    }

    /** @deprecated lifecycle UI removed; retained for transitional tests only */
    public ApiResponse<LifecycleSnapshot> lifecycleVerify(LifecycleRequest request) {
        return lifecycleService.verify(request);
    }

    /** @deprecated lifecycle UI removed; retained for transitional tests only */
    public ApiResponse<LifecycleSnapshot> lifecycleTamper(LifecycleRequest request) {
        return lifecycleService.tamper(request);
    }

    public ApiResponse<?> login(String username, String password) {
        if (!properties.isAuthEnabled()) {
            return ApiResponse.success(Collections.singletonMap("loggedIn", true), "鉴权未启用");
        }
        if (eq(properties.getUsername(), username) && eq(properties.getPassword(), password)) {
            return ApiResponse.success(Collections.singletonMap("loggedIn", true), "登录成功");
        }
        return ApiResponse.error(401, "用户名或密码错误");
    }

    public ApiResponse<?> meta() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", properties.getPath());
        data.put("authEnabled", properties.isAuthEnabled());
        data.put("allowedTables", new ArrayList<>(safeAllowedTables()));
        data.put("complexActions", complexActionCatalog());
        data.put("monitorPath", "/monitor");
        return ApiResponse.success(data);
    }

    public ApiResponse<?> datasources() {
        Map<String, DataSource> map = resolveAll();
        List<Map<String, Object>> list = new ArrayList<>();
        for (String id : map.keySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("primary", id.equals(defaultDatasourceId(map)));
            list.add(item);
        }
        return ApiResponse.success(list);
    }

    public ApiResponse<?> insert(CrudRequest request) {
        try {
            String table = requireAllowedTable(request.getTable());
            Map<String, Object> fields = sanitizeFields(request.getFields());
            if (fields.isEmpty()) {
                return ApiResponse.error(400, "fields 不能为空");
            }
            DataSource ds = resolve(request.getDatasourceId());
            String sql = buildInsertSql(table, fields);
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bindValues(ps, new ArrayList<>(fields.values()));
                int updated = ps.executeUpdate();
                Long id = readGeneratedKey(ps);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("updated", updated);
                result.put("id", id);
                result.put("table", table);
                return ApiResponse.success(result, "插入成功");
            }
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.warn("playground insert failed", e);
            return ApiResponse.error("插入失败: " + e.getMessage());
        }
    }

    public ApiResponse<?> update(CrudRequest request) {
        try {
            String table = requireAllowedTable(request.getTable());
            if (request.getId() == null) {
                return ApiResponse.error(400, "id 不能为空");
            }
            Map<String, Object> fields = sanitizeFields(request.getFields());
            if (fields.isEmpty()) {
                return ApiResponse.error(400, "fields 不能为空");
            }
            DataSource ds = resolve(request.getDatasourceId());
            StringBuilder sql = new StringBuilder("UPDATE ").append(quoteTable(table)).append(" SET ");
            List<Object> values = new ArrayList<>();
            boolean first = true;
            for (Map.Entry<String, Object> e : fields.entrySet()) {
                if (!first) {
                    sql.append(", ");
                }
                first = false;
                sql.append(quoteIdent(e.getKey())).append(" = ?");
                values.add(e.getValue());
            }
            sql.append(" WHERE ").append(quoteIdent("id")).append(" = ?");
            values.add(request.getId());
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                bindValues(ps, values);
                int updated = ps.executeUpdate();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("updated", updated);
                result.put("id", request.getId());
                return ApiResponse.success(result, "更新成功");
            }
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.warn("playground update failed", e);
            return ApiResponse.error("更新失败: " + e.getMessage());
        }
    }

    public ApiResponse<?> query(CrudRequest request) {
        return doQuery(request, false);
    }

    public ApiResponse<?> rawQuery(CrudRequest request) {
        return doQuery(request, true);
    }

    private ApiResponse<?> doQuery(CrudRequest request, boolean rawHint) {
        try {
            String table = requireAllowedTable(request.getTable());
            DataSource ds = resolve(request.getDatasourceId());
            int limit = request.getLimit() == null ? 50 : Math.min(Math.max(request.getLimit(), 1), 200);
            StringBuilder sql = new StringBuilder("SELECT * FROM ").append(quoteTable(table));
            List<Object> values = new ArrayList<>();
            if (request.getId() != null) {
                sql.append(" WHERE ").append(quoteIdent("id")).append(" = ?");
                values.add(request.getId());
            }
            sql.append(" ORDER BY ").append(quoteIdent("id")).append(" DESC LIMIT ?");
            values.add(limit);
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                bindValues(ps, values);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Map<String, Object>> rows = mapResultSet(rs);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("table", table);
                    result.put("rawHint", rawHint);
                    result.put("rows", rows);
                    result.put("count", rows.size());
                    return ApiResponse.success(result);
                }
            }
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.warn("playground query failed", e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    public ApiResponse<?> digestDemo(DigestDemoRequest request) {
        try {
            requireAllowedTable(DIGEST_TABLE);
            String action = request.getAction() == null ? "" : request.getAction().trim().toLowerCase(Locale.ROOT);
            DataSource ds = resolve(request.getDatasourceId());
            switch (action) {
                case "insert":
                    return digestInsert(ds, request);
                case "update-phone":
                    return digestUpdatePhone(ds, request);
                case "tamper":
                    return digestTamper(ds, request);
                case "verify-read":
                    return digestVerifyRead(ds, request);
                case "raw-query":
                    CrudRequest crud = new CrudRequest();
                    crud.setDatasourceId(request.getDatasourceId());
                    crud.setTable(DIGEST_TABLE);
                    crud.setId(request.getId());
                    crud.setLimit(20);
                    return rawQuery(crud);
                default:
                    return ApiResponse.error(400, "未知 digest action: " + request.getAction()
                            + "（支持 insert|update-phone|tamper|verify-read|raw-query）");
            }
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.warn("playground digest demo failed", e);
            return ApiResponse.error("Digest 演示失败: " + e.getMessage());
        }
    }

    public ApiResponse<?> complexRun(ComplexRunRequest request) {
        try {
            String action = request.getAction() == null ? "" : request.getAction().trim().toLowerCase(Locale.ROOT);
            switch (action) {
                case "like-user":
                    return complexLikeUser(request);
                case "page-user":
                    return complexPageUser(request);
                case "join-orders":
                    return complexJoinOrders(request);
                case "batch-insert-user":
                    return complexBatchInsert(request);
                case "multi-ds-compare":
                    return complexMultiDsCompare(request);
                default:
                    return ApiResponse.error(400, "未知 complex action: " + request.getAction()
                            + "（支持 like-user|page-user|join-orders|batch-insert-user|multi-ds-compare）");
            }
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.warn("playground complex run failed", e);
            return ApiResponse.error("复杂查询失败: " + e.getMessage());
        }
    }

    // ---------------- digest helpers ----------------

    private ApiResponse<?> digestInsert(DataSource ds, DigestDemoRequest request) throws Exception {
        String name = request.getName() != null ? request.getName() : "演示用户";
        String phone = request.getPhone() != null ? request.getPhone() : "13800000000";
        Integer age = request.getAge() != null ? request.getAge() : 28;
        String email = request.getEmail() != null ? request.getEmail() : "demo@example.com";
        String sql = "INSERT INTO " + quoteTable(DIGEST_TABLE)
                + " (" + quoteIdent("name") + ", " + quoteIdent("phone") + ", "
                + quoteIdent("age") + ", " + quoteIdent("email") + ") VALUES (?, ?, ?, ?)";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, phone);
            ps.setObject(3, age);
            ps.setString(4, email);
            ps.executeUpdate();
            Long id = readGeneratedKey(ps);
            Map<String, Object> stored = loadDigestRowRaw(ds, id);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("rawRow", stored);
            result.put("note", "未显式写入 row_digest 时，应由摘要链路自动生成");
            return ApiResponse.success(result, "Digest insert 完成");
        }
    }

    private ApiResponse<?> digestUpdatePhone(DataSource ds, DigestDemoRequest request) throws Exception {
        if (request.getId() == null) {
            return ApiResponse.error(400, "id 不能为空");
        }
        String phone = request.getPhone() != null ? request.getPhone() : "13900000000";
        String sql = "UPDATE " + quoteTable(DIGEST_TABLE) + " SET " + quoteIdent("phone")
                + " = ? WHERE " + quoteIdent("id") + " = ?";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            ps.setLong(2, request.getId());
            int updated = ps.executeUpdate();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("updated", updated);
            result.put("rawRow", loadDigestRowRaw(ds, request.getId()));
            return ApiResponse.success(result, "已更新 phone（摘要应随之重算）");
        }
    }

    private ApiResponse<?> digestTamper(DataSource ds, DigestDemoRequest request) throws Exception {
        if (request.getId() == null) {
            return ApiResponse.error(400, "id 不能为空");
        }
        String fake = request.getFakeDigest() != null ? request.getFakeDigest() : "TAMPERED_DIGEST_VALUE";
        // 使用 skip 注释尽量绕过加解密/摘要重写，便于演示篡改后验签失败
        String sql = "/* SECURT_SKIP */ UPDATE " + quoteTable(DIGEST_TABLE) + " SET " + quoteIdent("row_digest")
                + " = ? WHERE " + quoteIdent("id") + " = ?";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fake);
            ps.setLong(2, request.getId());
            int updated = ps.executeUpdate();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("updated", updated);
            result.put("rawRow", loadDigestRowRaw(ds, request.getId()));
            result.put("warning", "已写入伪造摘要，后续 verify-read 可能失败或告警");
            return ApiResponse.success(result, "摘要已篡改");
        }
    }

    private ApiResponse<?> digestVerifyRead(DataSource ds, DigestDemoRequest request) throws Exception {
        if (request.getId() == null) {
            return ApiResponse.error(400, "id 不能为空");
        }
        // 走拦截连接的 SELECT，触发验签（若配置了 verify）
        String sql = "SELECT * FROM " + quoteTable(DIGEST_TABLE) + " WHERE " + quoteIdent("id") + " = ?";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, request.getId());
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> rows = mapResultSet(rs);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("rows", rows);
                result.put("rawRow", loadDigestRowRaw(ds, request.getId()));
                return ApiResponse.success(result, "验签读取完成（失败时请查看服务端日志）");
            }
        }
    }

    private Map<String, Object> loadDigestRowRaw(DataSource ds, Long id) throws Exception {
        if (id == null) {
            return Collections.emptyMap();
        }
        String sql = "SELECT * FROM " + quoteTable(DIGEST_TABLE) + " WHERE " + quoteIdent("id") + " = ?";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> rows = mapResultSet(rs);
                return rows.isEmpty() ? Collections.<String, Object>emptyMap() : rows.get(0);
            }
        }
    }

    // ---------------- complex helpers ----------------

    private ApiResponse<?> complexLikeUser(ComplexRunRequest request) throws Exception {
        requireAllowedTable("user");
        String pattern = request.getPattern() != null ? request.getPattern() : "张";
        DataSource ds = resolve(request.getDatasourceId());
        String sql = "SELECT * FROM " + quoteTable("user") + " WHERE " + quoteIdent("name") + " LIKE ? LIMIT 50";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + pattern + "%");
            try (ResultSet rs = ps.executeQuery()) {
                return ApiResponse.success(mapResultSet(rs), "LIKE 查询完成");
            }
        }
    }

    private ApiResponse<?> complexPageUser(ComplexRunRequest request) throws Exception {
        requireAllowedTable("user");
        int size = request.getPageSize() == null ? 10 : Math.min(Math.max(request.getPageSize(), 1), 100);
        DataSource ds = resolve(request.getDatasourceId());
        String sql = "SELECT * FROM " + quoteTable("user") + " ORDER BY " + quoteIdent("id") + " DESC LIMIT ?";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, size);
            try (ResultSet rs = ps.executeQuery()) {
                return ApiResponse.success(mapResultSet(rs), "分页查询完成");
            }
        }
    }

    private ApiResponse<?> complexJoinOrders(ComplexRunRequest request) throws Exception {
        requireAllowedTable("user");
        String orderTable = resolveOrderTable();
        String userName = request.getUserName() != null ? request.getUserName() : "张";
        DataSource ds = resolve(request.getDatasourceId());
        String sql = "SELECT o.* FROM " + quoteTable(orderTable) + " o INNER JOIN " + quoteTable("user")
                + " u ON o." + quoteIdent("user_id") + " = u." + quoteIdent("id")
                + " WHERE u." + quoteIdent("name") + " LIKE ? LIMIT 50";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + userName + "%");
            try (ResultSet rs = ps.executeQuery()) {
                return ApiResponse.success(mapResultSet(rs), "JOIN 查询完成 (" + orderTable + ")");
            }
        }
    }

    private String resolveOrderTable() {
        for (String allowed : safeAllowedTables()) {
            if ("orders".equals(allowed) || "order".equals(allowed)) {
                return allowed;
            }
        }
        throw new IllegalArgumentException("当前工程不支持 JOIN：白名单中无 order/orders 表");
    }

    private ApiResponse<?> complexBatchInsert(ComplexRunRequest request) throws Exception {
        requireAllowedTable("user");
        int batch = request.getBatchSize() == null ? 3 : Math.min(Math.max(request.getBatchSize(), 1), 20);
        DataSource ds = resolve(request.getDatasourceId());
        String sql = "INSERT INTO " + quoteTable("user") + " (" + quoteIdent("name") + ", "
                + quoteIdent("phone") + ", " + quoteIdent("age") + ", " + quoteIdent("email")
                + ") VALUES (?, ?, ?, ?)";
        List<Long> ids = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < batch; i++) {
                ps.setString(1, "批量用户-" + System.currentTimeMillis() + "-" + i);
                ps.setString(2, "1370000000" + (i % 10));
                ps.setInt(3, 20 + i);
                ps.setString(4, "batch" + i + "@example.com");
                ps.addBatch();
            }
            ps.executeBatch();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                while (keys.next()) {
                    ids.add(keys.getLong(1));
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("insertedIds", ids);
        result.put("count", ids.size());
        return ApiResponse.success(result, "批量插入完成");
    }

    private ApiResponse<?> complexMultiDsCompare(ComplexRunRequest request) throws Exception {
        Map<String, DataSource> all = resolveAll();
        if (all.size() < 2) {
            return ApiResponse.error(400, "当前工程不支持：需要至少 2 个数据源（请在多数据源测试工程中使用）");
        }
        requireAllowedTable("user");
        Map<String, Object> byDs = new LinkedHashMap<>();
        for (Map.Entry<String, DataSource> e : all.entrySet()) {
            String sql = "SELECT COUNT(*) AS cnt FROM " + quoteTable("user");
            try (Connection conn = e.getValue().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                long cnt = 0;
                if (rs.next()) {
                    cnt = rs.getLong(1);
                }
                byDs.put(e.getKey(), cnt);
            }
        }
        return ApiResponse.success(byDs, "多数据源 user 行数对照");
    }

    // ---------------- resolve / allow-list ----------------

    public DataSource resolve(String datasourceId) {
        Map<String, DataSource> all = resolveAll();
        if (all.isEmpty()) {
            throw new IllegalArgumentException("未配置 DataSource");
        }
        if (datasourceId == null || datasourceId.trim().isEmpty()) {
            return all.get(defaultDatasourceId(all));
        }
        DataSource ds = all.get(datasourceId.trim());
        if (ds == null) {
            throw new IllegalArgumentException("未知数据源: " + datasourceId + "，可选: " + all.keySet());
        }
        return ds;
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

    String requireAllowedTable(String table) {
        if (table == null || table.trim().isEmpty()) {
            throw new IllegalArgumentException("table 不能为空");
        }
        String canonical = canonicalizeTable(table);
        for (String allowed : safeAllowedTables()) {
            if (canonicalizeTable(allowed).equals(canonical)) {
                return canonical;
            }
        }
        throw new IllegalArgumentException("表不在白名单: " + table + "，允许: " + safeAllowedTables());
    }

    private List<String> safeAllowedTables() {
        List<String> configured = properties.getAllowedTables();
        if (configured == null || configured.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String t : configured) {
            if (t != null && !t.trim().isEmpty()) {
                out.add(canonicalizeTable(t));
            }
        }
        return out;
    }

    static String canonicalizeTable(String table) {
        String t = table.trim();
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
            t = t.substring(1, t.length() - 1);
        }
        return t.toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> sanitizeFields(Map<String, Object> fields) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (fields == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String col = e.getKey().trim();
            if (!SAFE_COLUMNS.contains(col)) {
                throw new IllegalArgumentException("不允许的列: " + col);
            }
            if ("id".equalsIgnoreCase(col)) {
                continue;
            }
            out.put(col.toLowerCase(Locale.ROOT), e.getValue());
        }
        return out;
    }

    private List<Map<String, Object>> complexActionCatalog() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(actionMeta("like-user", "LIKE 查询 user.name", false));
        list.add(actionMeta("page-user", "分页查询 user", false));
        list.add(actionMeta("join-orders", "JOIN orders × user", false));
        list.add(actionMeta("batch-insert-user", "批量插入 user", false));
        list.add(actionMeta("multi-ds-compare", "多数据源 user 行数对照", true));
        return list;
    }

    private Map<String, Object> actionMeta(String id, String label, boolean multiDsOnly) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("label", label);
        m.put("multiDsOnly", multiDsOnly);
        return m;
    }

    // ---------------- SQL helpers ----------------

    private String buildInsertSql(String table, Map<String, Object> fields) {
        StringBuilder cols = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        boolean first = true;
        for (String col : fields.keySet()) {
            if (!first) {
                cols.append(", ");
                placeholders.append(", ");
            }
            first = false;
            cols.append(quoteIdent(col));
            placeholders.append("?");
        }
        return "INSERT INTO " + quoteTable(table) + " (" + cols + ") VALUES (" + placeholders + ")";
    }

    static String quoteTable(String table) {
        if ("user".equalsIgnoreCase(table) || "order".equalsIgnoreCase(table)) {
            return "\"" + table.toLowerCase(Locale.ROOT) + "\"";
        }
        return quoteIdent(table);
    }

    static String quoteIdent(String ident) {
        // H2 MODE=MySQL：未加引号的标识符会折叠；列名走白名单，无需强制双引号
        return ident.replace("\"", "").replace(";", "");
    }

    private void bindValues(PreparedStatement ps, List<Object> values) throws Exception {
        for (int i = 0; i < values.size(); i++) {
            Object v = values.get(i);
            if (v == null) {
                ps.setNull(i + 1, Types.NULL);
            } else {
                ps.setObject(i + 1, v);
            }
        }
    }

    private Long readGeneratedKey(PreparedStatement ps) throws Exception {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getLong(1);
            }
        }
        return null;
    }

    private List<Map<String, Object>> mapResultSet(ResultSet rs) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
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
        return rows;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
