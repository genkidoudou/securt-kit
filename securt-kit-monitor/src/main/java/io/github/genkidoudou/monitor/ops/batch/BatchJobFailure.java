package io.github.genkidoudou.monitor.ops.batch;

/**
 * 刷数作业单行失败记录。
 */
public class BatchJobFailure {
    private String jobId;
    private String pk;
    private String message;
    private long createdAt;

    public BatchJobFailure() {
    }

    public BatchJobFailure(String jobId, String pk, String message) {
        this.jobId = jobId;
        this.pk = pk;
        this.message = message;
        this.createdAt = System.currentTimeMillis();
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getPk() {
        return pk;
    }

    public void setPk(String pk) {
        this.pk = pk;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
