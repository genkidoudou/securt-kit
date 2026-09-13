package io.github.genkidoudou.monitor.ops.batch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BatchJobModelTest {

    @Test
    void constructsReadyJob() {
        BatchJob job = new BatchJob();
        job.setTable("user");
        job.setOp("encrypt");
        job.setStatus(BatchJobStatus.READY);
        job.setTotalEstimated(100);
        job.setChunkSize(200);

        assertEquals(BatchJobStatus.READY, job.getStatus());
        assertEquals("user", job.getTable());
        assertEquals(100, job.getTotalEstimated());
        assertNotNull(BatchJobStatus.values());
    }
}
