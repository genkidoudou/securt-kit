package io.github.genkidoudou.monitor.ops.batch;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JdbcBatchJobStoreTest {

    @Test
    void persistResumeAndNormalizeLeakedRunning() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:batch_job_store;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        JdbcBatchJobStore store = new JdbcBatchJobStore(ds);
        store.ensureSchema();

        BatchJob job = new BatchJob();
        job.setTable("user");
        job.setOp("encrypt");
        job.setIdColumn("id");
        job.setWhereClause("id > 0");
        job.setStatus(BatchJobStatus.READY);
        job.setTotalEstimated(100);
        job.setChunkSize(50);
        String id = store.create(job);

        BatchJob running = store.get(id);
        running.setStatus(BatchJobStatus.RUNNING);
        running.setCursorPk("42");
        running.setProcessed(42);
        store.update(running);

        JdbcBatchJobStore reloaded = new JdbcBatchJobStore(ds);
        reloaded.ensureSchema();
        BatchJob again = reloaded.get(id);
        assertNotNull(again);
        assertEquals(BatchJobStatus.RUNNING, again.getStatus());
        assertEquals("42", again.getCursorPk());

        reloaded.normalizeLeakedRunningToPaused();
        assertEquals(BatchJobStatus.PAUSED, reloaded.get(id).getStatus());
    }
}
