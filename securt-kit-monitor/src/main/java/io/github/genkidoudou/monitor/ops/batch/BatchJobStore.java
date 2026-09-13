package io.github.genkidoudou.monitor.ops.batch;

import java.util.List;

/**
 * 异步刷数作业仓储。
 */
public interface BatchJobStore {

    String create(BatchJob job);

    BatchJob get(String jobId);

    void update(BatchJob job);

    /**
     * 追加失败明细；若已达上限则不再写入明细。
     *
     * @return true 若写入了明细
     */
    boolean appendFailure(String jobId, BatchJobFailure failure, int maxFailureRecords);

    List<BatchJobFailure> listFailures(String jobId, int limit);

    /**
     * database 模式：将泄漏的 RUNNING/PAUSING/CANCELING 规范为 PAUSED。memory 模式可空操作。
     */
    void normalizeLeakedRunningToPaused();
}
