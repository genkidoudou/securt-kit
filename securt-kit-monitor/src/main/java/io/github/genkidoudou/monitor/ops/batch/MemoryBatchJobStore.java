package io.github.genkidoudou.monitor.ops.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存作业仓（TTL）；进程重启丢失。
 */
public class MemoryBatchJobStore implements BatchJobStore {

    public static final long DEFAULT_TTL_MS = 60 * 60 * 1000L;

    private final ConcurrentHashMap<String, BatchJob> jobs = new ConcurrentHashMap<String, BatchJob>();
    private final ConcurrentHashMap<String, List<BatchJobFailure>> failures =
            new ConcurrentHashMap<String, List<BatchJobFailure>>();
    private final long ttlMs;

    public MemoryBatchJobStore() {
        this(DEFAULT_TTL_MS);
    }

    public MemoryBatchJobStore(long ttlMs) {
        this.ttlMs = ttlMs > 0 ? ttlMs : DEFAULT_TTL_MS;
    }

    @Override
    public String create(BatchJob job) {
        purgeExpired();
        String id = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        job.setJobId(id);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        job.setExpiresAt(now + ttlMs);
        if (job.getStatus() == null) {
            job.setStatus(BatchJobStatus.READY);
        }
        jobs.put(id, copyJob(job));
        failures.put(id, new CopyOnWriteArrayList<BatchJobFailure>());
        return id;
    }

    @Override
    public BatchJob get(String jobId) {
        purgeExpired();
        if (jobId == null) {
            return null;
        }
        BatchJob job = jobs.get(jobId);
        return job == null ? null : copyJob(job);
    }

    @Override
    public void update(BatchJob job) {
        if (job == null || job.getJobId() == null) {
            return;
        }
        purgeExpired();
        BatchJob existing = jobs.get(job.getJobId());
        if (existing == null) {
            return;
        }
        BatchJob next = copyJob(job);
        next.setExpiresAt(existing.getExpiresAt());
        next.setCreatedAt(existing.getCreatedAt());
        next.setUpdatedAt(System.currentTimeMillis());
        jobs.put(job.getJobId(), next);
    }

    @Override
    public boolean appendFailure(String jobId, BatchJobFailure failure, int maxFailureRecords) {
        if (jobId == null || failure == null) {
            return false;
        }
        List<BatchJobFailure> list = failures.get(jobId);
        if (list == null) {
            return false;
        }
        if (maxFailureRecords > 0 && list.size() >= maxFailureRecords) {
            return false;
        }
        failure.setJobId(jobId);
        if (failure.getCreatedAt() <= 0) {
            failure.setCreatedAt(System.currentTimeMillis());
        }
        list.add(failure);
        return true;
    }

    @Override
    public List<BatchJobFailure> listFailures(String jobId, int limit) {
        List<BatchJobFailure> list = failures.get(jobId);
        if (list == null || list.isEmpty()) {
            return new ArrayList<BatchJobFailure>();
        }
        int n = limit <= 0 ? list.size() : Math.min(limit, list.size());
        return new ArrayList<BatchJobFailure>(list.subList(0, n));
    }

    @Override
    public void normalizeLeakedRunningToPaused() {
        // memory: no-op
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, BatchJob> e : jobs.entrySet()) {
            Long exp = e.getValue().getExpiresAt();
            if (exp != null && exp < now) {
                jobs.remove(e.getKey(), e.getValue());
                failures.remove(e.getKey());
            }
        }
    }

    static BatchJob copyJob(BatchJob src) {
        BatchJob j = new BatchJob();
        j.setJobId(src.getJobId());
        j.setDatasourceId(src.getDatasourceId());
        j.setTable(src.getTable());
        j.setOp(src.getOp());
        j.setWhereClause(src.getWhereClause());
        j.setIdColumn(src.getIdColumn());
        j.setFieldsCsv(src.getFieldsCsv());
        j.setStatus(src.getStatus());
        j.setTotalEstimated(src.getTotalEstimated());
        j.setProcessed(src.getProcessed());
        j.setSucceeded(src.getSucceeded());
        j.setFailed(src.getFailed());
        j.setCursorPk(src.getCursorPk());
        j.setChunkSize(src.getChunkSize());
        j.setErrorMessage(src.getErrorMessage());
        j.setCreatedAt(src.getCreatedAt());
        j.setUpdatedAt(src.getUpdatedAt());
        j.setExpiresAt(src.getExpiresAt());
        return j;
    }
}
