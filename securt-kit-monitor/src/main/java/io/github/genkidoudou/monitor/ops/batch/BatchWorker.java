package io.github.genkidoudou.monitor.ops.batch;

import io.github.genkidoudou.core.TableCache;
import io.github.genkidoudou.core.cache.StrategyCache;
import io.github.genkidoudou.core.config.DigestConfigRegistry;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.core.digest.DigestService;
import io.github.genkidoudou.core.digest.ResolvedDigestRule;
import io.github.genkidoudou.core.strategy.FieldEncryptorStrategy;
import io.github.genkidoudou.monitor.MonitorProperties;
import io.github.genkidoudou.monitor.ops.BatchGuard;
import io.github.genkidoudou.monitor.util.SqlIdentifierQuotes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 进程内刷数 worker：keyset 分批，批末响应 pause/cancel。
 */
public class BatchWorker {

    private static final Logger log = LoggerFactory.getLogger(BatchWorker.class);

    private final BatchJobStore store;
    private final DataSource dataSource;
    private final MonitorProperties properties;
    private final FieldEncryptorProperties encryptorProperties;
    private final DigestService digestService = new DigestService();
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, Boolean> inflight = new ConcurrentHashMap<String, Boolean>();

    public BatchWorker(BatchJobStore store,
                       DataSource dataSource,
                       MonitorProperties properties,
                       FieldEncryptorProperties encryptorProperties) {
        this.store = store;
        this.dataSource = dataSource;
        this.properties = properties != null ? properties : new MonitorProperties();
        this.encryptorProperties = encryptorProperties;
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "securtkit-batch-worker-" + n.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }

    public boolean trySchedule(String jobId) {
        if (jobId == null) {
            return false;
        }
        if (inflight.putIfAbsent(jobId, Boolean.TRUE) != null) {
            return false;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    runJob(jobId);
                } finally {
                    inflight.remove(jobId);
                }
            }
        });
        return true;
    }

    void runJob(String jobId) {
        BatchJob job = store.get(jobId);
        if (job == null) {
            return;
        }
        if (job.getStatus() == BatchJobStatus.CANCELING) {
            job.setStatus(BatchJobStatus.CANCELLED);
            store.update(job);
            return;
        }
        if (job.getStatus() == BatchJobStatus.PAUSING) {
            job.setStatus(BatchJobStatus.PAUSED);
            store.update(job);
            return;
        }
        if (job.getStatus() != BatchJobStatus.READY && job.getStatus() != BatchJobStatus.RUNNING) {
            return;
        }
        job.setStatus(BatchJobStatus.RUNNING);
        store.update(job);
        try {
            while (true) {
                job = store.get(jobId);
                if (job == null) {
                    return;
                }
                if (job.getStatus() == BatchJobStatus.PAUSING) {
                    job.setStatus(BatchJobStatus.PAUSED);
                    store.update(job);
                    return;
                }
                if (job.getStatus() == BatchJobStatus.CANCELING) {
                    job.setStatus(BatchJobStatus.CANCELLED);
                    store.update(job);
                    return;
                }
                if (job.getStatus() == BatchJobStatus.PAUSED || job.getStatus().isTerminal()) {
                    return;
                }
                if (job.getStatus() != BatchJobStatus.RUNNING) {
                    job.setStatus(BatchJobStatus.RUNNING);
                    store.update(job);
                }

                int chunk = processChunk(job);
                job = store.get(jobId);
                if (job == null) {
                    return;
                }
                if (job.getStatus() == BatchJobStatus.PAUSING) {
                    job.setStatus(BatchJobStatus.PAUSED);
                    store.update(job);
                    return;
                }
                if (job.getStatus() == BatchJobStatus.CANCELING) {
                    job.setStatus(BatchJobStatus.CANCELLED);
                    store.update(job);
                    return;
                }
                if (chunk == 0) {
                    job.setStatus(job.getFailed() > 0 ? BatchJobStatus.PARTIAL : BatchJobStatus.SUCCEEDED);
                    store.update(job);
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("batch job {} failed fatally", jobId, e);
            BatchJob failed = store.get(jobId);
            if (failed != null && !failed.getStatus().isTerminal()) {
                failed.setStatus(BatchJobStatus.FAILED);
                failed.setErrorMessage(e.getMessage());
                store.update(failed);
            }
        }
    }

    private int processChunk(BatchJob job) throws Exception {
        if (dataSource == null) {
            throw new IllegalStateException("数据源不可用");
        }
        MonitorProperties.BatchSettings batch = properties.getBatch() != null
                ? properties.getBatch() : new MonitorProperties.BatchSettings();
        int chunkSize = job.getChunkSize() > 0 ? job.getChunkSize() : batch.getChunkSize();
        int maxFailRec = batch.getMaxFailureRecords();
        String op = job.getOp() == null ? "" : job.getOp().trim().toLowerCase(Locale.ROOT);
        List<String> requestedFields = parseFieldsCsv(job.getFieldsCsv());

        try (Connection conn = dataSource.getConnection()) {
            String tableQ = SqlIdentifierQuotes.quoteResolvedTable(conn, job.getTable());
            String pkQ = SqlIdentifierQuotes.quoteResolvedColumn(conn, job.getTable(), job.getIdColumn());
            List<String> columns = resolvePreviewColumns(job.getTable(), job.getDatasourceId(), op,
                    job.getIdColumn(), requestedFields);
            if (columns.isEmpty()) {
                throw new IllegalStateException("无可处理字段");
            }
            StringBuilder selectList = new StringBuilder();
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    selectList.append(", ");
                }
                selectList.append(SqlIdentifierQuotes.quoteResolvedColumn(conn, job.getTable(), columns.get(i)));
            }
            String where = job.getWhereClause() == null ? "" : job.getWhereClause().trim();
            StringBuilder sql = new StringBuilder(skipCommentPrefix());
            sql.append("SELECT ").append(selectList).append(" FROM ").append(tableQ).append(" WHERE ");
            boolean hasUserWhere = !where.isEmpty();
            if (hasUserWhere) {
                sql.append("(").append(where).append(")");
            } else {
                sql.append("1=1");
            }
            Object cursor = decodeCursor(job.getCursorPk());
            if (cursor != null) {
                sql.append(" AND ").append(pkQ).append(" > ?");
            }
            sql.append(" ORDER BY ").append(pkQ).append(" LIMIT ").append(chunkSize);

            List<Map<String, Object>> rows;
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                if (cursor != null) {
                    ps.setObject(1, cursor);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    rows = mapRows(rs);
                }
            }
            if (rows.isEmpty()) {
                return 0;
            }

            Object lastPk = null;
            long processed = job.getProcessed();
            long succeeded = job.getSucceeded();
            long failed = job.getFailed();
            List<String> fieldNames = resolveEncryptFields(job.getTable(), requestedFields);

            for (Map<String, Object> row : rows) {
                Map<String, Object> before = projectRow(row, columns);
                Object pk = findValueIgnoreCase(before, job.getIdColumn());
                lastPk = pk;
                Map<String, Object> after = new LinkedHashMap<String, Object>(before);
                try {
                    applyOp(op, job, conn, pk, before, after, fieldNames);
                    Map<String, Object> sets = diffSets(before, after, job.getIdColumn());
                    if (!sets.isEmpty()) {
                        updateRow(conn, job.getTable(), job.getIdColumn(), pk, sets);
                    }
                    succeeded++;
                } catch (Exception ex) {
                    failed++;
                    store.appendFailure(job.getJobId(),
                            new BatchJobFailure(job.getJobId(), pk == null ? null : String.valueOf(pk), ex.getMessage()),
                            maxFailRec);
                }
                processed++;
            }

            job.setProcessed(processed);
            job.setSucceeded(succeeded);
            job.setFailed(failed);
            job.setCursorPk(lastPk == null ? null : String.valueOf(lastPk));
            store.update(job);
            return rows.size();
        }
    }

    private void applyOp(String op, BatchJob job, Connection conn, Object pk,
                         Map<String, Object> before, Map<String, Object> after,
                         List<String> fieldNames) {
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
                FieldEncryptorStrategy strategy = resolveFieldStrategy(job.getTable(), field);
                if (strategy == null) {
                    continue;
                }
                String s = String.valueOf(val);
                String next = "encrypt".equals(op) ? strategy.encryption(s) : strategy.decryption(s);
                after.put(key, next);
            }
            return;
        }
        if ("sign".equals(op)) {
            Map<String, String> plains = toStringMap(before);
            Map<String, String> digests = digestService.computeTargetDigests(
                    job.getTable(), job.getDatasourceId(), plains, false,
                    conn, job.getIdColumn() + " = ?", Collections.singletonList(pk));
            for (Map.Entry<String, String> e : digests.entrySet()) {
                String key = findKey(after, e.getKey());
                if (key == null) {
                    after.put(e.getKey(), e.getValue());
                } else {
                    after.put(key, e.getValue());
                }
            }
        }
    }

    private void updateRow(Connection conn, String table, String idColumn, Object pk,
                           Map<String, Object> sets) throws Exception {
        String tableQ = SqlIdentifierQuotes.quoteResolvedTable(conn, table);
        String pkQ = SqlIdentifierQuotes.quoteResolvedColumn(conn, table, idColumn);
        StringBuilder sql = new StringBuilder(skipCommentPrefix());
        sql.append("UPDATE ").append(tableQ).append(" SET ");
        List<Object> params = new ArrayList<Object>();
        boolean first = true;
        for (Map.Entry<String, Object> e : sets.entrySet()) {
            if (!first) {
                sql.append(", ");
            }
            first = false;
            sql.append(SqlIdentifierQuotes.quoteResolvedColumn(conn, table, e.getKey())).append(" = ?");
            params.add(e.getValue());
        }
        sql.append(" WHERE ").append(pkQ).append(" = ?");
        params.add(pk);
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ps.executeUpdate();
        }
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

    private List<String> resolveEncryptFields(String table, List<String> requested) {
        List<String> out = new ArrayList<String>();
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

    private List<String> resolvePreviewColumns(String table, String datasourceId, String op,
                                               String pkColumn, List<String> requestedFields) {
        LinkedHashMap<String, Boolean> ordered = new LinkedHashMap<String, Boolean>();
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
        return new ArrayList<String>(ordered.keySet());
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

    private static Map<String, Object> diffSets(Map<String, Object> before, Map<String, Object> after, String idColumn) {
        Map<String, Object> sets = new LinkedHashMap<String, Object>();
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

    private static List<String> parseFieldsCsv(String csv) {
        List<String> out = new ArrayList<String>();
        if (csv == null || csv.trim().isEmpty()) {
            return out;
        }
        for (String p : csv.split(",")) {
            if (p != null && !p.trim().isEmpty()) {
                out.add(p.trim());
            }
        }
        return out;
    }

    private static Object decodeCursor(String cursorPk) {
        if (cursorPk == null || cursorPk.trim().isEmpty()) {
            return null;
        }
        String s = cursorPk.trim();
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException ignore) {
            return s;
        }
    }

    private static Map<String, Object> projectRow(Map<String, Object> row, List<String> columns) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (row == null || columns == null) {
            return out;
        }
        for (String col : columns) {
            String key = findKey(row, col);
            out.put(col, key == null ? null : row.get(key));
        }
        return out;
    }

    private static List<Map<String, Object>> mapRows(ResultSet rs) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        ResultSetMetaData meta = rs.getMetaData();
        int count = meta.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
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
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (row == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            out.put(e.getKey(), e.getValue() == null ? null : String.valueOf(e.getValue()));
        }
        return out;
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

    public void shutdown() {
        executor.shutdownNow();
    }
}
