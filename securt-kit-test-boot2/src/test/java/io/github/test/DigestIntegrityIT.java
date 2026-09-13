package io.github.test;

import io.github.genkidoudou.core.exception.DigestMismatchException;
import io.github.genkidoudou.core.strategy.HmacSha256DigestStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = TestApplication.class)
class DigestIntegrityIT {

    private static final String TEST_TABLE = "digest_user";
    private static final String RAW_DB_URL =
            "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String HMAC_KEY = "boot2-digest-integration-secret";

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void resetUserTable() throws Exception {
        try (Connection connection = rawConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + TEST_TABLE);
            statement.execute("CREATE TABLE " + TEST_TABLE + " ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(100) NOT NULL, "
                    + "phone VARCHAR(100), "
                    + "age INT, "
                    + "email VARCHAR(100), "
                    + "row_digest VARCHAR(128))");
        }
    }

    @Test
    void insertWithoutDigestColumnPopulatesDigest() throws Exception {
        long id = insertUser("张三", "13800138000");

        assertNotNull(rawDigest(id));
        assertEquals(expectedDigest("13800138000"), rawDigest(id));
    }

    @Test
    void updatingPhoneRecomputesDigestWithReloadPolicy() throws Exception {
        long id = insertUser("李四", "13900139000");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement("UPDATE " + TEST_TABLE + " SET phone = ? WHERE id = ?")) {
            statement.setString(1, "13900139001");
            statement.setLong(2, id);
            assertEquals(1, statement.executeUpdate());
        }

        assertEquals(expectedDigest("13900139001"), rawDigest(id));
    }

    @Test
    void tamperedDigestFailsFastWhenRowIsRead() throws Exception {
        long id = insertUser("王五", "15000150000");
        try (Connection connection = rawConnection();
             PreparedStatement statement =
                     connection.prepareStatement("UPDATE " + TEST_TABLE + " SET row_digest = ? WHERE id = ?")) {
            statement.setString(1, "tampered");
            statement.setLong(2, id);
            assertEquals(1, statement.executeUpdate());
        }

        assertThrows(DigestMismatchException.class, () -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT id, name, phone, row_digest FROM " + TEST_TABLE + " WHERE id = ?")) {
                statement.setLong(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    resultSet.getString("phone");
                }
            }
        });
    }

    private long insertUser(String name, String phone) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO " + TEST_TABLE + " (name, phone, age, email) VALUES (?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setString(2, phone);
            statement.setInt(3, 30);
            statement.setString(4, name + "@example.com");
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private String rawDigest(long id) throws Exception {
        try (Connection connection = rawConnection();
             PreparedStatement statement =
                     connection.prepareStatement("SELECT row_digest FROM " + TEST_TABLE + " WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        }
    }

    private String expectedDigest(String phone) {
        return new HmacSha256DigestStrategy(HMAC_KEY)
                .digest(Collections.singletonMap("phone", phone));
    }

    private Connection rawConnection() throws Exception {
        return DriverManager.getConnection(RAW_DB_URL, "sa", "");
    }
}
