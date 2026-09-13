package io.github.genkidoudou.monitor.ops.batch;

import io.github.genkidoudou.monitor.MonitorProperties;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchJobStoreFactoryTest {

    @Test
    void defaultIsMemory() {
        BatchJobStore store = BatchJobStoreFactory.create(new MonitorProperties(), null);
        assertTrue(store instanceof MemoryBatchJobStore);
    }

    @Test
    void unknownModeFailsClearly() {
        MonitorProperties props = new MonitorProperties();
        props.getBatch().setStore("redis");
        assertThrows(IllegalArgumentException.class, () -> BatchJobStoreFactory.create(props, null));
    }

    @Test
    void databaseModeRequiresDataSource() {
        MonitorProperties props = new MonitorProperties();
        props.getBatch().setStore("database");
        assertThrows(IllegalStateException.class, () -> BatchJobStoreFactory.create(props, null));
    }

    @Test
    void databaseModeCreatesJdbcStore() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:batch_factory;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        MonitorProperties props = new MonitorProperties();
        props.getBatch().setStore("database");
        BatchJobStore store = BatchJobStoreFactory.create(props, ds);
        assertTrue(store instanceof JdbcBatchJobStore);
    }
}
