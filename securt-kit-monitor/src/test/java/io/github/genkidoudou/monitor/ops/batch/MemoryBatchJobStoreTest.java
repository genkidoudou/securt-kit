package io.github.genkidoudou.monitor.ops.batch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryBatchJobStoreTest {

    @Test
    void createGetUpdateAndExpire() throws Exception {
        MemoryBatchJobStore store = new MemoryBatchJobStore(50L);
        BatchJob job = new BatchJob();
        job.setTable("user");
        job.setOp("encrypt");
        job.setStatus(BatchJobStatus.READY);
        String id = store.create(job);
        assertNotNull(store.get(id));

        BatchJob loaded = store.get(id);
        loaded.setStatus(BatchJobStatus.RUNNING);
        loaded.setProcessed(10);
        store.update(loaded);
        assertEquals(BatchJobStatus.RUNNING, store.get(id).getStatus());
        assertEquals(10L, store.get(id).getProcessed());

        Thread.sleep(80L);
        assertNull(store.get(id));
    }

    @Test
    void failureDetailCap() {
        MemoryBatchJobStore store = new MemoryBatchJobStore();
        BatchJob job = new BatchJob();
        job.setTable("user");
        job.setOp("decrypt");
        String id = store.create(job);
        assertTrue(store.appendFailure(id, new BatchJobFailure(id, "1", "a"), 2));
        assertTrue(store.appendFailure(id, new BatchJobFailure(id, "2", "b"), 2));
        assertFalse(store.appendFailure(id, new BatchJobFailure(id, "3", "c"), 2));
        assertEquals(2, store.listFailures(id, 10).size());
    }
}
