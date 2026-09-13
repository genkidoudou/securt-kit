package io.github.genkidoudou.monitor.ops;

import io.github.genkidoudou.core.TableCache;
import io.github.genkidoudou.core.cache.StrategyCache;
import io.github.genkidoudou.core.config.DigestConfigRegistry;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.core.digest.DigestService;
import io.github.genkidoudou.core.digest.ResolvedDigestRule;
import io.github.genkidoudou.core.strategy.FieldEncryptorStrategy;
import io.github.genkidoudou.monitor.MonitorProperties;
import io.github.genkidoudou.monitor.dto.ApiResponse;
import io.github.genkidoudou.monitor.dto.BatchApplyRequest;
import io.github.genkidoudou.monitor.dto.BatchPreviewRequest;
import io.github.genkidoudou.monitor.dto.DigestToolRequest;
import io.github.genkidoudou.monitor.dto.PrimaryKeyInfo;
import io.github.genkidoudou.monitor.dto.RowVerifyRequest;
import io.github.genkidoudou.monitor.ops.batch.BatchJob;
import io.github.genkidoudou.monitor.ops.batch.BatchJobFailure;
import io.github.genkidoudou.monitor.ops.batch.BatchJobStatus;
import io.github.genkidoudou.monitor.ops.batch.BatchJobStore;
import io.github.genkidoudou.monitor.ops.batch.BatchJobStoreFactory;
import io.github.genkidoudou.monitor.ops.batch.BatchWorker;
import io.github.genkidoudou.monitor.util.SqlIdentifierQuotes;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Monitor 运维能力：摘要工具、单行验签、异步刷数作业。
 */
@Slf4j
public class MonitorOpsFacade {

    private final MonitorProperties properties;
    private final FieldEncryptorProperties encryptorProperties;
    private final DataSource dataSource;
    private final BatchJobStore jobStore;
    private final BatchWorker batchWorker;
    private final DigestService digestService = new DigestService();

    public MonitorOpsFacade(MonitorProperties properties,
                            FieldEncryptorProperties encryptorProperties,
                            DataSource dataSource) {
        this.properties = properties != null ? properties : new MonitorProperties();
        this.encryptorProperties = encryptorProperties;
        this.dataSource = dataSource;
        this.jobStore = BatchJobStoreFactory.create(this.properties, dataSource);
        this.batchWorker = new BatchWorker(this.jobStore, dataSource, this.properties, encryptorProperties);
    }

    public ApiResponse<?> resolvePrimaryKey(String table, String idColumn) {
        PrimaryKeyInfo info = PrimaryKeyResolver.resolve(properties, table, idColumn);
        return ApiResponse.success(info);
    }

    public ApiResponse<?> digestTool(DigestToolRequest request) {
        if (request == null || request.getAction() == null) {
            return ApiResponse.error(400, "action 不能为空");
        }
        String action = request.getAction().trim().toLowerCase(Locale.ROOT);
        String table = request.getTableName();
        boolean plainMode = table == null || table.trim().isEmpty();
        try {
            if (plainMode) {
                return digestPlaintext(action, request);
            }
            Map<String, String> sources = request.getSourceValues() == null
                    ? Collections.<String, String>emptyMap()
                    : request.getSourceValues();
            Map<String, String> digests = digestService.computeTargetDigests(
                    table, request.getDatasourceId(), sources, true, null, null, null);
            if (digests.isEmpty()) {
                return ApiResponse.error(400, "表未配置摘要规则或无法计算: " + table);
            }
            if ("sign".equals(action)) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("digests", digests);
                if (request.getTargetField() != null) {
                    data.put("digest", digests.get(request.getTargetField().toLowerCase(Locale.ROOT)));
                } else if (digests.size() == 1) {
                    data.put("digest", digests.values().iterator().next());
                }
                return ApiResponse.success(data, "签名成功");
            }
            if ("verify".equals(action)) {
                String target = request.getTargetField();
                String expected = request.getExpectedDigest();
                if (expected == null || expected.trim().isEmpty()) {
                    return ApiResponse.error(400, "expectedDigest 不能为空");
                }
                String actual;
                if (target != null && !target.trim().isEmpty()) {
                    actual = digests.get(target.toLowerCase(Locale.ROOT));
                } else {
                    actual = digests.values().iterator().next();
                }
                boolean ok = expected.equals(actual);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("verified", ok);
                data.put("expectedDigest", expected);
                data.put("actualDigest", actual);
                data.put("digests", digests);
                return ApiResponse.success(data, ok ? "验签通过" : "验签失败");
            }
            return ApiResponse.error(400, "未知 action: " + request.getAction());
        } catch (Exception e) {
            log.warn("digest tool failed", e);
            return ApiResponse.error("摘要操作失败: " + e.getMessage());
        }
    }

    /**
     * 无表配置：用全局 digest-strategy 对纯文本签名/验签。
     */
    private ApiResponse<?> digestPlaintext(String action, DigestToolRequest request) {
        String text = request.getPlaintext();
        if (text == null || text.isEmpty()) {
            Map<String, String> sources = request.getSourceValues();
            if (sources != null && !sources.isEmpty()) {
                text = sources.values().iterator().next();
            }
        }
        if (text == null || text.isEmpty()) {
            return ApiResponse.error(400, "请输入要签名/验签的内容");
        }
        FieldEncryptorStrategy strategy = resolveGlobalDigestStrategy();
        if (strategy == null || !strategy.supportsDigest()) {
            return ApiResponse.error(400, "未配置可用的全局 digest-strategy");
        }
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
        ordered.put("content", text);
        String digest = strategy.digest(ordered);
        if ("sign".equals(action)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("digest", digest);
            return ApiResponse.success(data, "签名成功");
        }
        if ("verify".equals(action)) {
            String expected = request.getExpectedDigest();
            if (expected == null || expected.trim().isEmpty()) {
                return ApiResponse.error(400, "验签需要填写期望摘要");
            }
            boolean ok = strategy.verifyDigest(ordered, expected.trim());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("verified", ok);
            data.put("expectedDigest", expected.trim());
            data.put("actualDigest", digest);
            return ApiResponse.success(data, ok ? "验签通过" : "验签失败");
        }
        return ApiResponse.error(400, "未知 action: " + action);
    }

    private FieldEncryptorStrategy resolveGlobalDigestStrategy() {
        if (encryptorProperties == null) {
            return null;
        }
        String className = encryptorProperties.getDigestStrategy();
        if (className == null || className.trim().isEmpty()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Class<? extends FieldEncryptorStrategy> clazz =
                    (Class<? extends FieldEncryptorStrategy>) Class.forName(className.trim());
            return StrategyCache.getStrategy(clazz);
        } catch (Exception e) {
            log.warn("resolve global digest strategy failed: {}", className, e);
            return null;
        }
    }

    /**
     * 按表+id 加载行，并抽出摘要相关字段值（供验签页「查询 / 生成摘要 / 验签」）。
     */
    public ApiResponse<?> rowLoad(RowVerifyRequest request) {
        if (request == null || request.getTable() == null || request.getId() == null) {
            return ApiResponse.error(400, "table 与 id 不能为空");
        }
        if (dataSource == null) {
            return ApiResponse.error("数据源不可用");
        }
        String tableErr = BatchGuard.assertAllowedTable(encryptorProperties, properties, request.getTable());
        if (tableErr != null) {
            return ApiResponse.error(400, tableErr);
        }
        PrimaryKeyInfo pk = PrimaryKeyResolver.resolve(properties, request.getTable(), request.getIdColumn());
        if (pk.getColumn() == null) {
            return ApiResponse.error(400, "无法解析主键列，请配置 table-primary-keys 或传入 idColumn");
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT * FROM " + SqlIdentifierQuotes.quoteResolvedTable(conn, request.getTable())
                    + " WHERE " + SqlIdentifierQuotes.quoteResolvedColumn(conn, request.getTable(), pk.getColumn())
                    + " = ?";
            Map<String, Object> row;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, request.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    List<Map<String, Object>> rows = mapRows(rs);
                    if (rows.isEmpty()) {
                        return ApiResponse.error(404, "行不存在");
                    }
                    row = rows.get(0);
                }
            }
            Map<String, String> stringRow = toStringMap(row);
            List<ResolvedDigestRule> rules = DigestConfigRegistry.getRules(
                    request.getTable(), request.getDatasourceId());
            List<Map<String, Object>> digestColumns = new ArrayList<>();
            Map<String, String> sourceValues = new LinkedHashMap<>();
            for (ResolvedDigestRule rule : rules) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("targetField", rule.getTargetField());
                item.put("sourceFields", rule.getSourceFields());
                item.put("storedDigest", findIgnoreCase(stringRow, rule.getTargetField()));
                LinkedHashMap<String, String> sources = new LinkedHashMap<>();
                for (String sf : rule.getSourceFields()) {
                    String v = findIgnoreCase(stringRow, sf);
                    sources.put(sf, v);
                    sourceValues.put(sf, v);
                }
                item.put("sourceValues", sources);
                digestColumns.add(item);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("table", request.getTable());
            data.put("id", request.getId());
            data.put("idColumn", pk.getColumn());
            data.put("row", row);
            data.put("digestColumns", digestColumns);
            data.put("sourceValues", sourceValues);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.warn("row load failed", e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    public ApiResponse<?> rowVerify(RowVerifyRequest request) {
        if (request == null || request.getTable() == null || request.getId() == null) {
            return ApiResponse.error(400, "table 与 id 不能为空");
        }
        if (dataSource == null) {
            return ApiResponse.error("数据源不可用");
        }
        String tableErr = BatchGuard.assertAllowedTable(encryptorProperties, properties, request.getTable());
        if (tableErr != null) {
            return ApiResponse.error(400, tableErr);
        }
        PrimaryKeyInfo pk = PrimaryKeyResolver.resolve(properties, request.getTable(), request.getIdColumn());
        if (pk.getColumn() == null) {
            return ApiResponse.error(400, "无法解析主键列，请配置 table-primary-keys 或传入 idColumn");
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT * FROM " + SqlIdentifierQuotes.quoteResolvedTable(conn, request.getTable())
                    + " WHERE " + SqlIdentifierQuotes.quoteResolvedColumn(conn, request.getTable(), pk.getColumn())
                    + " = ?";
            Map<String, Object> row;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, request.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    List<Map<String, Object>> rows = mapRows(rs);
                    if (rows.isEmpty()) {
                        return ApiResponse.error(404, "行不存在");
                    }
                    row = rows.get(0);
                }
            }
            // MYBATIS 等模式下 JDBC 不解密：验签前按配置解密摘要源相关密文字段
            Map<String, Object> decryptedRow = MonitorResultDecryptor.decryptConfiguredColumns(
                    request.getDatasourceId(), request.getTable(), row);
            Map<String, String> stringRow = toStringMap(decryptedRow);
            Map<String, String> expected = digestService.computeTargetDigests(
                    request.getTable(), request.getDatasourceId(), stringRow, false,
                    conn, pk.getColumn() + " = ?", Collections.singletonList(request.getId()));
            List<Map<String, Object>> details = new ArrayList<>();
            boolean allOk = true;
            List<ResolvedDigestRule> rules = DigestConfigRegistry.getRules(
                    request.getTable(), request.getDatasourceId());
            if (rules.isEmpty()) {
                return ApiResponse.error(400, "表未配置摘要规则");
            }
            for (ResolvedDigestRule rule : rules) {
                String target = rule.getTargetField();
                // 摘要目标列取库内原值（通常未加密）
                String actual = findIgnoreCase(toStringMap(row), target);
                String expect = expected.get(target.toLowerCase(Locale.ROOT));
                boolean ok = expect != null && expect.equals(actual);
                if (!ok) {
                    // 也尝试用策略直接验签（明文源已在 stringRow）
                    LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
                    for (String sf : rule.getSourceFields()) {
                        ordered.put(sf, findIgnoreCase(stringRow, sf));
                    }
                    FieldEncryptorStrategy strategy = StrategyCache.getStrategy(rule.getStrategyClass());
                    ok = actual != null && strategy.verifyDigest(ordered, actual);
                    expect = strategy.digest(ordered);
                }
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("targetField", target);
                d.put("verified", ok);
                d.put("storedDigest", actual);
                d.put("expectedDigest", expect);
                details.add(d);
                if (!ok) {
                    allOk = false;
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("verified", allOk);
            data.put("primaryKey", pk);
            data.put("row", decryptedRow);
            data.put("details", details);
            return ApiResponse.success(data, allOk ? "验签通过" : "验签失败");
        } catch (Exception e) {
            log.warn("row verify failed", e);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("verified", false);
            data.put("error", e.getMessage());
            return ApiResponse.success(data, "验签失败: " + e.getMessage());
        }
    }

    public ApiResponse<?> batchPreview(BatchPreviewRequest request) {
        if (request == null) {
            return ApiResponse.error(400, "请求不能为空");
        }
        if (dataSource == null) {
            return ApiResponse.error("数据源不可用");
        }
        String tableErr = BatchGuard.assertAllowedTable(encryptorProperties, properties, request.getTable());
        if (tableErr != null) {
            return ApiResponse.error(400, tableErr);
        }
        String whereErr = BatchGuard.validateWhere(request.getWhere(), properties);
        if (whereErr != null) {
            return ApiResponse.error(400, whereErr);
        }
        String op = request.getOp() == null ? "" : request.getOp().trim().toLowerCase(Locale.ROOT);
        if (!("encrypt".equals(op) || "decrypt".equals(op) || "sign".equals(op))) {
            return ApiResponse.error(400, "op 必须是 encrypt|decrypt|sign");
        }
        PrimaryKeyInfo pk = PrimaryKeyResolver.resolve(properties, request.getTable(), request.getIdColumn());
        if (pk.getColumn() == null) {
            return ApiResponse.error(400, "无法解析主键列，请配置或传入 idColumn");
        }
        String where = BatchGuard.normalizeWhere(request.getWhere());
        MonitorProperties.BatchSettings batch = properties.getBatch() != null
                ? properties.getBatch() : new MonitorProperties.BatchSettings();
        int sampleSize = Math.max(1, batch.getSampleSize());
        try (Connection conn = dataSource.getConnection()) {
            String tableQ = SqlIdentifierQuotes.quoteResolvedTable(conn, request.getTable());
            String pkQ = SqlIdentifierQuotes.quoteResolvedColumn(conn, request.getTable(), pk.getColumn());
            List<String> previewColumns = resolvePreviewColumns(request.getTable(), request.getDatasourceId(),
                    op, pk.getColumn(), request.getFields());
            if (previewColumns.isEmpty()) {
                return ApiResponse.error(400, "无可预览字段：请确认表已配置加密字段或摘要规则");
            }
            long totalEstimated = countRows(conn, tableQ, where);
            StringBuilder selectList = new StringBuilder();
            for (int i = 0; i < previewColumns.size(); i++) {
                if (i > 0) {
                    selectList.append(", ");
                }
                selectList.append(SqlIdentifierQuotes.quoteResolvedColumn(
                        conn, request.getTable(), previewColumns.get(i)));
            }
            String skipPrefix = skipCommentPrefix();
            String sql = skipPrefix + "SELECT " + selectList + " FROM " + tableQ
                    + (where.isEmpty() ? "" : (" WHERE " + where))
                    + " ORDER BY " + pkQ + " LIMIT " + sampleSize;
            List<Map<String, Object>> rows;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                rows = mapRows(rs);
            }
            List<String> fieldNames = resolveEncryptFields(request.getTable(), request.getFields());
            List<Map<String, Object>> rowViews = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> projected = projectRow(row, previewColumns);
                Map<String, Object> before = new LinkedHashMap<>(projected);
                Map<String, Object> after = new LinkedHashMap<>(projected);
                Object rowPk = findValueIgnoreCase(projected, pk.getColumn());
                if ("encrypt".equals(op) || "decrypt".equals(op)) {
                    for (String field : fieldNames) {
                        String key = findKey(after, field);
                        if (key == null) {
                            continue;
                        }
                        Object val = after.get(key);
                        if (val == null) {
                            continue;
                        }
                        FieldEncryptorStrategy strategy = resolveFieldStrategy(request.getTable(), field);
                        if (strategy == null) {
                            continue;
                        }
                        String s = String.valueOf(val);
                        String next = "encrypt".equals(op) ? strategy.encryption(s) : strategy.decryption(s);
                        after.put(key, next);
                    }
                } else {
                    Map<String, String> plains = toStringMap(before);
                    Map<String, String> digests = digestService.computeTargetDigests(
                            request.getTable(), request.getDatasourceId(), plains, false,
                            conn, pk.getColumn() + " = ?", Collections.singletonList(rowPk));
                    for (Map.Entry<String, String> e : digests.entrySet()) {
                        String key = findKey(after, e.getKey());
                        if (key == null) {
                            after.put(e.getKey(), e.getValue());
                        } else {
                            after.put(key, e.getValue());
                        }
                    }
                }
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("pk", rowPk);
                view.put("before", before);
                view.put("after", after);
                rowViews.add(view);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("op", op);
            data.put("table", request.getTable());
            data.put("primaryKey", pk);
            data.put("columns", previewColumns);
            data.put("sampleSize", sampleSize);
            data.put("sampleCount", rowViews.size());
            data.put("totalEstimated", totalEstimated);
            data.put("maxRows", batch.getMaxRows());
            data.put("store", batch.getStore());
            data.put("rows", rowViews);
            return ApiResponse.success(data, "预览成功（样本，未写库，未创建作业）");
        } catch (Exception e) {
            log.warn("batch preview failed", e);
            return ApiResponse.error("预览失败: " + e.getMessage());
        }
    }

    public ApiResponse<?> batchCreateJob(BatchPreviewRequest request) {
        if (request == null) {
            return ApiResponse.error(400, "请求不能为空");
        }
        if (dataSource == null) {
            return ApiResponse.error("数据源不可用");
        }
        String tableErr = BatchGuard.assertAllowedTable(encryptorProperties, properties, request.getTable());
        if (tableErr != null) {
            return ApiResponse.error(400, tableErr);
        }
        String whereErr = BatchGuard.validateWhere(request.getWhere(), properties);
        if (whereErr != null) {
            return ApiResponse.error(400, whereErr);
        }
        String op = request.getOp() == null ? "" : request.getOp().trim().toLowerCase(Locale.ROOT);
        if (!("encrypt".equals(op) || "decrypt".equals(op) || "sign".equals(op))) {
            return ApiResponse.error(400, "op 必须是 encrypt|decrypt|sign");
        }
        PrimaryKeyInfo pk = PrimaryKeyResolver.resolve(properties, request.getTable(), request.getIdColumn());
        if (pk.getColumn() == null) {
            return ApiResponse.error(400, "无法解析主键列，请配置或传入 idColumn");
        }
        String where = BatchGuard.normalizeWhere(request.getWhere());
        MonitorProperties.BatchSettings batch = properties.getBatch() != null
                ? properties.getBatch() : new MonitorProperties.BatchSettings();
        try (Connection conn = dataSource.getConnection()) {
            String tableQ = SqlIdentifierQuotes.quoteResolvedTable(conn, request.getTable());
            long totalEstimated = countRows(conn, tableQ, where);
            if (totalEstimated > batch.getMaxRows()) {
                return ApiResponse.error(400, "匹配行数 " + totalEstimated + " 超过上限 " + batch.getMaxRows());
            }
            BatchJob job = new BatchJob();
            job.setDatasourceId(request.getDatasourceId());
            job.setTable(request.getTable());
            job.setOp(op);
            job.setWhereClause(where);
            job.setIdColumn(pk.getColumn());
            job.setFieldsCsv(joinFields(request.getFields()));
            job.setStatus(BatchJobStatus.READY);
            job.setTotalEstimated(totalEstimated);
            job.setChunkSize(batch.getChunkSize());
            String jobId = jobStore.create(job);
            batchWorker.trySchedule(jobId);
            Map<String, Object> data = jobStatusPayload(jobStore.get(jobId));
            return ApiResponse.success(data, "作业已创建");
        } catch (Exception e) {
            log.warn("batch create job failed", e);
            return ApiResponse.error("创建作业失败: " + e.getMessage());
        }
    }

    public ApiResponse<?> batchJobStatus(String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) {
            return ApiResponse.error(400, "jobId 不能为空");
        }
        BatchJob job = jobStore.get(jobId.trim());
        if (job == null) {
            return ApiResponse.error(400, "作业不存在或已过期");
        }
        return ApiResponse.success(jobStatusPayload(job), "ok");
    }

    public ApiResponse<?> batchPause(String jobId) {
        return transitionControl(jobId, "pause");
    }

    public ApiResponse<?> batchResume(String jobId) {
        return transitionControl(jobId, "resume");
    }

    public ApiResponse<?> batchCancel(String jobId) {
        return transitionControl(jobId, "cancel");
    }

    /**
     * @deprecated 已改为异步 jobs API；保留端点仅返回引导错误。
     */
    @Deprecated
    public ApiResponse<?> batchApply(BatchApplyRequest request) {
        return ApiResponse.error(400, "同步 apply 已废弃，请使用 POST /api/batch/jobs.json 创建异步作业后轮询状态");
    }

    private ApiResponse<?> transitionControl(String jobId, String action) {
        if (jobId == null || jobId.trim().isEmpty()) {
            return ApiResponse.error(400, "jobId 不能为空");
        }
        BatchJob job = jobStore.get(jobId.trim());
        if (job == null) {
            return ApiResponse.error(400, "作业不存在或已过期");
        }
        if ("pause".equals(action)) {
            if (job.getStatus() == BatchJobStatus.RUNNING || job.getStatus() == BatchJobStatus.READY) {
                job.setStatus(BatchJobStatus.PAUSING);
                storeAndMaybeSchedule(job, false);
            } else if (job.getStatus() != BatchJobStatus.PAUSING && job.getStatus() != BatchJobStatus.PAUSED) {
                return ApiResponse.error(400, "当前状态不可暂停: " + job.getStatus());
            }
        } else if ("resume".equals(action)) {
            if (job.getStatus() == BatchJobStatus.PAUSED) {
                job.setStatus(BatchJobStatus.READY);
                storeAndMaybeSchedule(job, true);
            } else {
                return ApiResponse.error(400, "仅 PAUSED 可继续，当前: " + job.getStatus());
            }
        } else if ("cancel".equals(action)) {
            if (job.getStatus().isTerminal()) {
                return ApiResponse.error(400, "作业已结束: " + job.getStatus());
            }
            if (job.getStatus() == BatchJobStatus.PAUSED || job.getStatus() == BatchJobStatus.READY) {
                job.setStatus(BatchJobStatus.CANCELLED);
                jobStore.update(job);
            } else {
                job.setStatus(BatchJobStatus.CANCELING);
                jobStore.update(job);
            }
        }
        return ApiResponse.success(jobStatusPayload(jobStore.get(jobId.trim())), "ok");
    }

    private void storeAndMaybeSchedule(BatchJob job, boolean schedule) {
        jobStore.update(job);
        if (schedule) {
            batchWorker.trySchedule(job.getJobId());
        }
    }

    private Map<String, Object> jobStatusPayload(BatchJob job) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (job == null) {
            return data;
        }
        data.put("jobId", job.getJobId());
        data.put("table", job.getTable());
        data.put("op", job.getOp());
        data.put("status", job.getStatus() == null ? null : job.getStatus().name());
        data.put("totalEstimated", job.getTotalEstimated());
        data.put("processed", job.getProcessed());
        data.put("succeeded", job.getSucceeded());
        data.put("failed", job.getFailed());
        data.put("cursorPk", job.getCursorPk());
        data.put("chunkSize", job.getChunkSize());
        data.put("errorMessage", job.getErrorMessage());
        data.put("updatedAt", job.getUpdatedAt());
        List<BatchJobFailure> fails = jobStore.listFailures(job.getJobId(), 20);
        List<Map<String, Object>> failViews = new ArrayList<>();
        for (BatchJobFailure f : fails) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pk", f.getPk());
            m.put("message", f.getMessage());
            failViews.add(m);
        }
        data.put("recentFailures", failViews);
        return data;
    }

    private long countRows(Connection conn, String tableQ, String where) throws Exception {
        String sql = skipCommentPrefix() + "SELECT COUNT(*) FROM " + tableQ
                + (where == null || where.isEmpty() ? "" : (" WHERE " + where));
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0L;
    }

    private static String joinFields(List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String f : fields) {
            if (f == null || f.trim().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(f.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private Map<String, Object> diffSets(Map<String, Object> before, Map<String, Object> after, String idColumn) {
        Map<String, Object> sets = new LinkedHashMap<>();
        if (after == null) {
            return sets;
        }
        for (Map.Entry<String, Object> e : after.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            if (BatchGuard.equalsIgnoreCase(e.getKey(), idColumn)) {
                continue;
            }
            Object old = before == null ? null : before.get(findKey(before, e.getKey()));
            Object neu = e.getValue();
            if (old == null && neu == null) {
                continue;
            }
            if (old != null && neu != null && String.valueOf(old).equals(String.valueOf(neu))) {
                continue;
            }
            sets.put(e.getKey(), neu);
        }
        return sets;
    }

    private List<String> resolveEncryptFields(String table, List<String> requested) {
        List<String> out = new ArrayList<>();
        if (requested != null && !requested.isEmpty()) {
            out.addAll(requested);
            return out;
        }
        Map<String, Class<? extends FieldEncryptorStrategy>> map =
                TableCache.getTableFieldEncryptInfo(table, null);
        if (map != null) {
            out.addAll(map.keySet());
        }
        return out;
    }

    /**
     * 预览列：加解密=主键+加密字段；摘要=主键+源字段+摘要字段。
     */
    private List<String> resolvePreviewColumns(String table, String datasourceId, String op,
                                               String pkColumn, List<String> requestedFields) {
        LinkedHashMap<String, Boolean> ordered = new LinkedHashMap<>();
        if (pkColumn != null && !pkColumn.trim().isEmpty()) {
            ordered.put(pkColumn.trim(), Boolean.TRUE);
        }
        if ("sign".equals(op)) {
            List<ResolvedDigestRule> rules = DigestConfigRegistry.getRules(table, datasourceId);
            for (ResolvedDigestRule rule : rules) {
                if (rule.getSourceFields() != null) {
                    for (String sf : rule.getSourceFields()) {
                        if (sf != null && !sf.trim().isEmpty()) {
                            ordered.put(sf.trim(), Boolean.TRUE);
                        }
                    }
                }
                if (rule.getTargetField() != null && !rule.getTargetField().trim().isEmpty()) {
                    ordered.put(rule.getTargetField().trim(), Boolean.TRUE);
                }
            }
        } else {
            for (String f : resolveEncryptFields(table, requestedFields)) {
                if (f != null && !f.trim().isEmpty()) {
                    ordered.put(f.trim(), Boolean.TRUE);
                }
            }
        }
        return new ArrayList<>(ordered.keySet());
    }

    private static Map<String, Object> projectRow(Map<String, Object> row, List<String> columns) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (row == null || columns == null) {
            return out;
        }
        for (String col : columns) {
            String key = findKey(row, col);
            out.put(col, key == null ? null : row.get(key));
        }
        return out;
    }

    private String skipCommentPrefix() {
        if (encryptorProperties != null
                && encryptorProperties.getSkipComment() != null
                && encryptorProperties.getSkipComment().isEnable()) {
            String token = encryptorProperties.getSkipComment().getToken();
            if (token == null || token.trim().isEmpty()) {
                token = "SECURT_SKIP";
            }
            return "/* " + token.trim() + " */ ";
        }
        return "/* SECURT_SKIP */ ";
    }

    private FieldEncryptorStrategy resolveFieldStrategy(String table, String field) {
        Map<String, Class<? extends FieldEncryptorStrategy>> map =
                TableCache.getTableFieldEncryptInfo(table, null);
        if (map != null) {
            for (Map.Entry<String, Class<? extends FieldEncryptorStrategy>> e : map.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(field)) {
                    return StrategyCache.getStrategy(e.getValue());
                }
            }
        }
        return StrategyCache.getStrategy(FieldEncryptorStrategy.class);
    }

    private static List<Map<String, Object>> mapRows(ResultSet rs) throws Exception {
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

    private static Map<String, String> toStringMap(Map<String, Object> row) {
        Map<String, String> out = new LinkedHashMap<>();
        if (row == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            out.put(e.getKey(), e.getValue() == null ? null : String.valueOf(e.getValue()));
        }
        return out;
    }

    private static String findIgnoreCase(Map<String, String> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (key.equalsIgnoreCase(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String findKey(Map<String, ?> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        for (String k : map.keySet()) {
            if (key.equalsIgnoreCase(k)) {
                return k;
            }
        }
        return null;
    }

    private static Object findValueIgnoreCase(Map<String, ?> map, String key) {
        String found = findKey(map, key);
        return found == null ? null : map.get(found);
    }
}
