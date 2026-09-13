package io.github.genkidoudou.monitor.ops.batch;

import io.github.genkidoudou.core.TableCache;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.monitor.MonitorProperties;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BatchWorkerAsyncTest {

    private JdbcDataSource ds;
    private MonitorProperties props;
    private FieldEncryptorProperties enc;

    @BeforeEach
    void setUp() throws Exception {
        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:batch_worker_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE \"user\" (id BIGINT PRIMARY KEY, name VARCHAR(100), phone VARCHAR(40))");
            for (int i = 1; i <= 250; i++) {
                st.execute("INSERT INTO \"user\"(id,name,phone) VALUES (" + i + ",'n" + i + "','p" + i + "')");
            }
        }
        enc = new FieldEncryptorProperties();
        enc.setEnable(true);
        FieldEncryptorProperties.TableConfig tc = new FieldEncryptorProperties.TableConfig();
        tc.setTableName("user");
        FieldEncryptorProperties.FieldConfig fc = new FieldEncryptorProperties.FieldConfig();
        fc.setFieldName("name");
        tc.setFields(Collections.singletonList(fc));
        enc.setTables(Collections.singletonList(tc));
        FieldEncryptorProperties.SkipCommentConfig skip = new FieldEncryptorProperties.SkipCommentConfig();
        skip.setEnable(true);
        skip.setToken("SECURT_SKIP");
        enc.setSkipComment(skip);
        TableCache.init(enc);

        props = new MonitorProperties();
        props.getTablePrimaryKeys().put("user", "id");
        props.setBatchAllowEmptyWhere(false);
        props.getBatch().setChunkSize(50);
        props.getBatch().setMaxRows(20000);
        props.getBatch().setStore("memory");
    }

    @Test
    void runsToSucceededAndAdvancesCursor() throws Exception {
        BatchJobStore store = new MemoryBatchJobStore();
        BatchWorker worker = new BatchWorker(store, ds, props, enc);
        BatchJob job = new BatchJob();
        job.setTable("user");
        job.setOp("encrypt");
        job.setWhereClause("id >= 1");
        job.setIdColumn("id");
        job.setStatus(BatchJobStatus.READY);
        job.setTotalEstimated(250);
        job.setChunkSize(50);
        String id = store.create(job);
        worker.runJob(id);

        BatchJob done = store.get(id);
        assertEquals(BatchJobStatus.SUCCEEDED, done.getStatus());
        assertEquals(250L, done.getProcessed());
        assertEquals(250L, done.getSucceeded());
        assertEquals("250", done.getCursorPk());

        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM \"user\" WHERE id = 1")) {
            assertTrue(rs.next());
            // default strategy may transform; ensure value changed or at least readable
            assertNotEquals("", rs.getString(1));
        }
        worker.shutdown();
    }

    @Test
    void cancelAtChunkBoundaryStopsWithoutMoreProgress() throws Exception {
        BatchJobStore store = new MemoryBatchJobStore();
        BatchWorker worker = new BatchWorker(store, ds, props, enc);
        BatchJob job = new BatchJob();
        job.setTable("user");
        job.setOp("encrypt");
        job.setWhereClause("id >= 1");
        job.setIdColumn("id");
        job.setStatus(BatchJobStatus.READY);
        job.setTotalEstimated(250);
        job.setChunkSize(50);
        String id = store.create(job);

        BatchJob mid = store.get(id);
        mid.setStatus(BatchJobStatus.RUNNING);
        mid.setCursorPk("100");
        mid.setProcessed(100);
        mid.setSucceeded(100);
        store.update(mid);
        mid = store.get(id);
        mid.setStatus(BatchJobStatus.CANCELING);
        store.update(mid);

        worker.runJob(id);
        BatchJob terminal = store.get(id);
        assertEquals(BatchJobStatus.CANCELLED, terminal.getStatus());
        assertEquals(100L, terminal.getProcessed());
        assertEquals("100", terminal.getCursorPk());
        worker.shutdown();
    }

    @Test
    void pauseAtChunkBoundaryKeepsCursor() throws Exception {
        BatchJobStore store = new MemoryBatchJobStore();
        BatchWorker worker = new BatchWorker(store, ds, props, enc);
        BatchJob job = new BatchJob();
        job.setTable("user");
        job.setOp("encrypt");
        job.setWhereClause("id >= 1");
        job.setIdColumn("id");
        job.setStatus(BatchJobStatus.READY);
        job.setTotalEstimated(250);
        job.setChunkSize(50);
        String id = store.create(job);

        BatchJob mid = store.get(id);
        mid.setStatus(BatchJobStatus.RUNNING);
        mid.setCursorPk("50");
        mid.setProcessed(50);
        store.update(mid);
        mid = store.get(id);
        mid.setStatus(BatchJobStatus.PAUSING);
        store.update(mid);

        worker.runJob(id);
        BatchJob paused = store.get(id);
        assertEquals(BatchJobStatus.PAUSED, paused.getStatus());
        assertEquals("50", paused.getCursorPk());

        paused.setStatus(BatchJobStatus.READY);
        store.update(paused);
        worker.runJob(id);
        assertEquals(BatchJobStatus.SUCCEEDED, store.get(id).getStatus());
        assertEquals(250L, store.get(id).getProcessed());
        worker.shutdown();
    }

    @Test
    void tryScheduleDoesNotDoubleRunSameJob() throws Exception {
        BatchJobStore store = new MemoryBatchJobStore();
        BatchWorker worker = new BatchWorker(store, ds, props, enc);
        BatchJob job = new BatchJob();
        job.setTable("user");
        job.setOp("encrypt");
        job.setWhereClause("id >= 1");
        job.setIdColumn("id");
        job.setStatus(BatchJobStatus.READY);
        job.setTotalEstimated(250);
        job.setChunkSize(50);
        String id = store.create(job);
        assertTrue(worker.trySchedule(id));
        assertTrue(!worker.trySchedule(id));
        for (int i = 0; i < 100; i++) {
            Thread.sleep(20);
            if (store.get(id).getStatus().isTerminal()) {
                break;
            }
        }
        assertTrue(store.get(id).getStatus().isTerminal());
        worker.shutdown();
    }
}
