package io.github.genkidoudou.monitor.ops.batch;

/**
 * 异步刷数作业。
 */
public class BatchJob {
    private String jobId;
    private String datasourceId;
    private String table;
    private String op;
    private String whereClause;
    private String idColumn;
    private String fieldsCsv;
    private BatchJobStatus status = BatchJobStatus.READY;
    private long totalEstimated;
    private long processed;
    private long succeeded;
    private long failed;
    private String cursorPk;
    private int chunkSize = 200;
    private String errorMessage;
    private long createdAt;
    private long updatedAt;
    private Long expiresAt;

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getDatasourceId() {
        return datasourceId;
    }

    public void setDatasourceId(String datasourceId) {
        this.datasourceId = datasourceId;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public String getWhereClause() {
        return whereClause;
    }

    public void setWhereClause(String whereClause) {
        this.whereClause = whereClause;
    }

    public String getIdColumn() {
        return idColumn;
    }

    public void setIdColumn(String idColumn) {
        this.idColumn = idColumn;
    }

    public String getFieldsCsv() {
        return fieldsCsv;
    }

    public void setFieldsCsv(String fieldsCsv) {
        this.fieldsCsv = fieldsCsv;
    }

    public BatchJobStatus getStatus() {
        return status;
    }

    public void setStatus(BatchJobStatus status) {
        this.status = status;
    }

    public long getTotalEstimated() {
        return totalEstimated;
    }

    public void setTotalEstimated(long totalEstimated) {
        this.totalEstimated = totalEstimated;
    }

    public long getProcessed() {
        return processed;
    }

    public void setProcessed(long processed) {
        this.processed = processed;
    }

    public long getSucceeded() {
        return succeeded;
    }

    public void setSucceeded(long succeeded) {
        this.succeeded = succeeded;
    }

    public long getFailed() {
        return failed;
    }

    public void setFailed(long failed) {
        this.failed = failed;
    }

    public String getCursorPk() {
        return cursorPk;
    }

    public void setCursorPk(String cursorPk) {
        this.cursorPk = cursorPk;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
