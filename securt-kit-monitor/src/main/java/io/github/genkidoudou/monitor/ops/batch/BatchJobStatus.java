package io.github.genkidoudou.monitor.ops.batch;

/**
 * 异步刷数作业状态。
 */
public enum BatchJobStatus {
    READY,
    RUNNING,
    PAUSING,
    PAUSED,
    CANCELING,
    CANCELLED,
    SUCCEEDED,
    PARTIAL,
    FAILED;

    public boolean isTerminal() {
        return this == CANCELLED || this == SUCCEEDED || this == PARTIAL || this == FAILED;
    }

    public static BatchJobStatus fromName(String name) {
        if (name == null) {
            return null;
        }
        return BatchJobStatus.valueOf(name.trim().toUpperCase());
    }
}
