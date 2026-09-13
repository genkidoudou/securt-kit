package io.github.genkidoudou.monitor.ops.batch;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 目标库持久化作业仓（H2 / MySQL）。
 */
public class JdbcBatchJobStore implements BatchJobStore {

    public static final String JOB_TABLE = "securtkit_monitor_batch_job";
    public static final String FAILURE_TABLE = "securtkit_monitor_batch_failure";

    private final DataSource dataSource;
    private volatile boolean schemaReady;

    public JdbcBatchJobStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource required");
        }
        this.dataSource = dataSource;
    }

    public void ensureSchema() throws SQLException {
        if (schemaReady) {
            return;
        }
        synchronized (this) {
            if (schemaReady) {
                return;
            }
            try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS " + JOB_TABLE + " ("
                        + "job_id VARCHAR(64) PRIMARY KEY,"
                        + "datasource_id VARCHAR(128),"
                        + "table_name VARCHAR(128) NOT NULL,"
                        + "op VARCHAR(32) NOT NULL,"
                        + "where_clause CLOB,"
                        + "id_column VARCHAR(128),"
                        + "fields_csv VARCHAR(1024),"
                        + "status VARCHAR(32) NOT NULL,"
                        + "total_estimated BIGINT,"
                        + "processed BIGINT,"
                        + "succeeded BIGINT,"
                        + "failed BIGINT,"
                        + "cursor_pk VARCHAR(256),"
                        + "chunk_size INT,"
                        + "error_message VARCHAR(2000),"
                        + "created_at BIGINT,"
                        + "updated_at BIGINT"
                        + ")");
                st.execute("CREATE TABLE IF NOT EXISTS " + FAILURE_TABLE + " ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "job_id VARCHAR(64) NOT NULL,"
                        + "pk_value VARCHAR(256),"
                        + "message VARCHAR(2000),"
                        + "created_at BIGINT"
                        + ")");
                st.execute("CREATE INDEX IF NOT EXISTS idx_securtkit_batch_fail_job ON "
                        + FAILURE_TABLE + " (job_id)");
            }
            schemaReady = true;
        }
    }

    private void ensure() {
        try {
            ensureSchema();
        } catch (SQLException e) {
            throw new IllegalStateException("ensureSchema failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String create(BatchJob job) {
        ensure();
        String id = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        job.setJobId(id);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        if (job.getStatus() == null) {
            job.setStatus(BatchJobStatus.READY);
        }
        String sql = "INSERT INTO " + JOB_TABLE
                + " (job_id,datasource_id,table_name,op,where_clause,id_column,fields_csv,status,"
                + "total_estimated,processed,succeeded,failed,cursor_pk,chunk_size,error_message,created_at,updated_at)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindJob(ps, job);
            ps.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new IllegalStateException("create job failed: " + e.getMessage(), e);
        }
    }

    @Override
    public BatchJob get(String jobId) {
        if (jobId == null) {
            return null;
        }
        ensure();
        String sql = "SELECT * FROM " + JOB_TABLE + " WHERE job_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapJob(rs);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("get job failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(BatchJob job) {
        if (job == null || job.getJobId() == null) {
            return;
        }
        ensure();
        job.setUpdatedAt(System.currentTimeMillis());
        String sql = "UPDATE " + JOB_TABLE + " SET datasource_id=?, table_name=?, op=?, where_clause=?, id_column=?,"
                + " fields_csv=?, status=?, total_estimated=?, processed=?, succeeded=?, failed=?, cursor_pk=?,"
                + " chunk_size=?, error_message=?, updated_at=? WHERE job_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, job.getDatasourceId());
            ps.setString(i++, job.getTable());
            ps.setString(i++, job.getOp());
            ps.setString(i++, job.getWhereClause());
            ps.setString(i++, job.getIdColumn());
            ps.setString(i++, job.getFieldsCsv());
            ps.setString(i++, job.getStatus() == null ? null : job.getStatus().name());
            ps.setLong(i++, job.getTotalEstimated());
            ps.setLong(i++, job.getProcessed());
            ps.setLong(i++, job.getSucceeded());
            ps.setLong(i++, job.getFailed());
            ps.setString(i++, job.getCursorPk());
            ps.setInt(i++, job.getChunkSize());
            ps.setString(i++, job.getErrorMessage());
            ps.setLong(i++, job.getUpdatedAt());
            ps.setString(i, job.getJobId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("update job failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean appendFailure(String jobId, BatchJobFailure failure, int maxFailureRecords) {
        if (jobId == null || failure == null) {
            return false;
        }
        ensure();
        int count = countFailures(jobId);
        if (maxFailureRecords > 0 && count >= maxFailureRecords) {
            return false;
        }
        String sql = "INSERT INTO " + FAILURE_TABLE + " (job_id, pk_value, message, created_at) VALUES (?,?,?,?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jobId);
            ps.setString(2, failure.getPk());
            ps.setString(3, failure.getMessage());
            ps.setLong(4, failure.getCreatedAt() > 0 ? failure.getCreatedAt() : System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            throw new IllegalStateException("appendFailure failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<BatchJobFailure> listFailures(String jobId, int limit) {
        ensure();
        String sql = "SELECT job_id, pk_value, message, created_at FROM " + FAILURE_TABLE
                + " WHERE job_id = ? ORDER BY id ASC";
        if (limit > 0) {
            sql = sql + " LIMIT " + limit;
        }
        List<BatchJobFailure> out = new ArrayList<BatchJobFailure>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BatchJobFailure f = new BatchJobFailure();
                    f.setJobId(rs.getString("job_id"));
                    f.setPk(rs.getString("pk_value"));
                    f.setMessage(rs.getString("message"));
                    f.setCreatedAt(rs.getLong("created_at"));
                    out.add(f);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("listFailures failed: " + e.getMessage(), e);
        }
        return out;
    }

    @Override
    public void normalizeLeakedRunningToPaused() {
        ensure();
        String sql = "UPDATE " + JOB_TABLE + " SET status=?, updated_at=? WHERE status IN (?,?,?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            ps.setString(1, BatchJobStatus.PAUSED.name());
            ps.setLong(2, now);
            ps.setString(3, BatchJobStatus.RUNNING.name());
            ps.setString(4, BatchJobStatus.PAUSING.name());
            ps.setString(5, BatchJobStatus.CANCELING.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("normalizeLeakedRunningToPaused failed: " + e.getMessage(), e);
        }
    }

    private int countFailures(String jobId) {
        String sql = "SELECT COUNT(*) FROM " + FAILURE_TABLE + " WHERE job_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("countFailures failed: " + e.getMessage(), e);
        }
        return 0;
    }

    private void bindJob(PreparedStatement ps, BatchJob job) throws SQLException {
        int i = 1;
        ps.setString(i++, job.getJobId());
        ps.setString(i++, job.getDatasourceId());
        ps.setString(i++, job.getTable());
        ps.setString(i++, job.getOp());
        if (job.getWhereClause() == null) {
            ps.setNull(i++, Types.CLOB);
        } else {
            ps.setString(i++, job.getWhereClause());
        }
        ps.setString(i++, job.getIdColumn());
        ps.setString(i++, job.getFieldsCsv());
        ps.setString(i++, job.getStatus().name());
        ps.setLong(i++, job.getTotalEstimated());
        ps.setLong(i++, job.getProcessed());
        ps.setLong(i++, job.getSucceeded());
        ps.setLong(i++, job.getFailed());
        ps.setString(i++, job.getCursorPk());
        ps.setInt(i++, job.getChunkSize());
        ps.setString(i++, job.getErrorMessage());
        ps.setLong(i++, job.getCreatedAt());
        ps.setLong(i, job.getUpdatedAt());
    }

    private BatchJob mapJob(ResultSet rs) throws SQLException {
        BatchJob j = new BatchJob();
        j.setJobId(rs.getString("job_id"));
        j.setDatasourceId(rs.getString("datasource_id"));
        j.setTable(rs.getString("table_name"));
        j.setOp(rs.getString("op"));
        j.setWhereClause(rs.getString("where_clause"));
        j.setIdColumn(rs.getString("id_column"));
        j.setFieldsCsv(rs.getString("fields_csv"));
        j.setStatus(BatchJobStatus.fromName(rs.getString("status")));
        j.setTotalEstimated(rs.getLong("total_estimated"));
        j.setProcessed(rs.getLong("processed"));
        j.setSucceeded(rs.getLong("succeeded"));
        j.setFailed(rs.getLong("failed"));
        j.setCursorPk(rs.getString("cursor_pk"));
        j.setChunkSize(rs.getInt("chunk_size"));
        j.setErrorMessage(rs.getString("error_message"));
        j.setCreatedAt(rs.getLong("created_at"));
        j.setUpdatedAt(rs.getLong("updated_at"));
        return j;
    }
}
