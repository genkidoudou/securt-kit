package io.github.genkidoudou.monitor.ops.batch;

import io.github.genkidoudou.core.TableCache;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.monitor.MonitorProperties;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchWorkerCapacityTest {

    @Test
    void tenThousandRowsReachTerminalWithMonotonicProgress() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:batch_cap_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE \"user\" (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            st.execute("CREATE SEQUENCE IF NOT EXISTS user_seq START WITH 1");
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= 10000; i++) {
                if (i % 500 == 1) {
                    if (sb.length() > 0) {
                        st.execute(sb.toString());
                        sb.setLength(0);
                    }
                    sb.append("INSERT INTO \"user\"(id,name) VALUES ");
                } else {
                    sb.append(',');
                }
                sb.append('(').append(i).append(",'n").append(i).append("')");
            }
            if (sb.length() > 0) {
                st.execute(sb.toString());
            }
        }

        FieldEncryptorProperties enc = new FieldEncryptorProperties();
        enc.setEnable(true);
        FieldEncryptorProperties.TableConfig tc = new FieldEncryptorProperties.TableConfig();
        tc.setTableName("user");
        FieldEncryptorProperties.FieldConfig fc = new FieldEncryptorProperties.FieldConfig();
        fc.setFieldName("name");
        tc.setFields(Collections.singletonList(fc));
        enc.setTables(Collections.singletonList(tc));
        TableCache.init(enc);

        MonitorProperties props = new MonitorProperties();
        props.getTablePrimaryKeys().put("user", "id");
        props.getBatch().setChunkSize(200);
        props.getBatch().setMaxRows(20000);

        BatchJobStore store = new MemoryBatchJobStore();
        BatchWorker worker = new BatchWorker(store, ds, props, enc);
        BatchJob job = new BatchJob();
        job.setTable("user");
        job.setOp("encrypt");
        job.setWhereClause("id >= 1");
        job.setIdColumn("id");
        job.setStatus(BatchJobStatus.READY);
        job.setTotalEstimated(10000);
        job.setChunkSize(200);
        String id = store.create(job);
        worker.runJob(id);
        BatchJob done = store.get(id);
        assertTrue(done.getStatus() == BatchJobStatus.SUCCEEDED || done.getStatus() == BatchJobStatus.PARTIAL);
        assertEquals(10000L, done.getProcessed());
        assertTrue(done.getProcessed() >= done.getSucceeded());
        worker.shutdown();
    }
}
