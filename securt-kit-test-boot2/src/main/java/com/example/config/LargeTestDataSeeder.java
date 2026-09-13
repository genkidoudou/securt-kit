package com.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 启动后幂等补齐 user / digest_user / orders 测试数据，便于刷数与 Monitor 大表联调。
 * <p>
 * 走应用 DataSource（含拦截器），插入明文后由加密策略写入密文/摘要。
 * 在 {@link ApplicationReadyEvent} 触发，晚于现有 CommandLineRunner，避免拖慢启动演示查询。
 */
@Component
public class LargeTestDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(LargeTestDataSeeder.class);

    private static final String[] ORDER_STATUSES = {"PAID", "SHIPPED", "DELIVERED", "PENDING"};

    private final JdbcTemplate jdbcTemplate;

    @Value("${test-data.seed.enabled:true}")
    private boolean enabled;

    @Value("${test-data.seed.target-rows:10000}")
    private int targetRows;

    @Value("${test-data.seed.batch-size:500}")
    private int batchSize;

    public LargeTestDataSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedOnReady() {
        if (!enabled) {
            log.info("LargeTestDataSeeder skipped (test-data.seed.enabled=false)");
            return;
        }
        if (targetRows <= 0) {
            log.warn("LargeTestDataSeeder skipped (target-rows={})", targetRows);
            return;
        }
        int batch = Math.max(1, batchSize);
        long started = System.currentTimeMillis();
        int users = topUpUsers(batch);
        int digests = topUpDigestUsers(batch);
        int orders = topUpOrders(batch);
        log.info("LargeTestDataSeeder done: user+={}, digest_user+={}, orders+={}, elapsed={}ms",
            users, digests, orders, System.currentTimeMillis() - started);
    }

    private int topUpUsers(int batch) {
        int current = count("SELECT COUNT(*) FROM \"user\"");
        int gap = targetRows - current;
        if (gap <= 0) {
            log.info("user already has {} rows (>= {}), skip", current, targetRows);
            return 0;
        }
        log.info("Seeding user: current={}, inserting {}", current, gap);
        String sql = "INSERT INTO \"user\" (name, phone, age, email) VALUES (?, ?, ?, ?)";
        int start = current + 1;
        for (int offset = 0; offset < gap; offset += batch) {
            int end = Math.min(offset + batch, gap);
            List<Object[]> rows = new ArrayList<>(end - offset);
            for (int i = offset; i < end; i++) {
                int seq = start + i;
                rows.add(new Object[]{
                    "用户" + seq,
                    phoneOf(seq),
                    18 + (seq % 50),
                    "user" + seq + "@example.com"
                });
            }
            jdbcTemplate.batchUpdate(sql, rows);
        }
        return gap;
    }

    private int topUpDigestUsers(int batch) {
        int current = count("SELECT COUNT(*) FROM digest_user");
        int gap = targetRows - current;
        if (gap <= 0) {
            log.info("digest_user already has {} rows (>= {}), skip", current, targetRows);
            return 0;
        }
        log.info("Seeding digest_user: current={}, inserting {}", current, gap);
        // row_digest 由拦截器按 phone 摘要规则生成
        String sql = "INSERT INTO digest_user (name, phone, age, email) VALUES (?, ?, ?, ?)";
        int start = current + 1;
        for (int offset = 0; offset < gap; offset += batch) {
            int end = Math.min(offset + batch, gap);
            List<Object[]> rows = new ArrayList<>(end - offset);
            for (int i = offset; i < end; i++) {
                int seq = start + i;
                rows.add(new Object[]{
                    "摘要用户" + seq,
                    phoneOf(1_000_000 + seq),
                    18 + (seq % 50),
                    "digest" + seq + "@example.com"
                });
            }
            jdbcTemplate.batchUpdate(sql, rows);
        }
        return gap;
    }

    private int topUpOrders(int batch) {
        int current = count("SELECT COUNT(*) FROM orders");
        int gap = targetRows - current;
        if (gap <= 0) {
            log.info("orders already has {} rows (>= {}), skip", current, targetRows);
            return 0;
        }
        Long minUserId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM \"user\"", Long.class);
        Long maxUserId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM \"user\"", Long.class);
        if (minUserId == null || maxUserId == null) {
            log.warn("orders seed skipped: no users present");
            return 0;
        }
        long userSpan = maxUserId - minUserId + 1;
        log.info("Seeding orders: current={}, inserting {}, userId range=[{}, {}]",
            current, gap, minUserId, maxUserId);
        String sql = "INSERT INTO orders (user_id, order_no, customer_name, customer_phone, amount, status) "
            + "VALUES (?, ?, ?, ?, ?, ?)";
        int start = current + 1;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int offset = 0; offset < gap; offset += batch) {
            int end = Math.min(offset + batch, gap);
            List<Object[]> rows = new ArrayList<>(end - offset);
            for (int i = offset; i < end; i++) {
                int seq = start + i;
                long userId = minUserId + ((seq - 1) % userSpan);
                String phone = phoneOf(seq);
                rows.add(new Object[]{
                    userId,
                    String.format("ORD%010d", seq),
                    "客户" + seq,
                    phone,
                    BigDecimal.valueOf(10 + random.nextDouble(990))
                        .setScale(2, RoundingMode.HALF_UP),
                    ORDER_STATUSES[seq % ORDER_STATUSES.length]
                });
            }
            jdbcTemplate.batchUpdate(sql, rows);
        }
        return gap;
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    /** 11 位手机号，加密后缀后仍落在 VARCHAR(20) 内。 */
    static String phoneOf(int seq) {
        return String.format("13%09d", Math.floorMod(seq, 1_000_000_000));
    }
}
