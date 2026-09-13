package io.github.genkidoudou.core.interceptor;

import cn.hutool.core.lang.Pair;
import io.github.genkidoudou.core.TableCache;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.core.crypto.FieldCryptoServiceHolder;
import io.github.genkidoudou.core.parser.SecurtkitUtils;
import io.github.genkidoudou.core.parser.dto.ColumnTableDto;
import io.github.genkidoudou.core.parser.dto.FieldEncryptorInfoDto;
import io.github.genkidoudou.core.strategy.FieldEncryptorStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JDBC ResultSet 函数投影解密（同包访问 {@link ResultSetDecryptingProxy#wrap}）。
 */
class ResultSetFunctionProjectionDecryptTest {

    @BeforeEach
    void setUp() {
        TableCache.reset();
        FieldCryptoServiceHolder.reset();
        FieldEncryptorProperties properties = new FieldEncryptorProperties();
        properties.setEnable(true);
        FieldEncryptorProperties.TableConfig table = new FieldEncryptorProperties.TableConfig();
        table.setTableName("user");
        FieldEncryptorProperties.FieldConfig phone = new FieldEncryptorProperties.FieldConfig();
        phone.setFieldName("phone");
        phone.setStrategy(PrefixStrategy.class.getName());
        table.setFields(Collections.singletonList(phone));
        properties.setTables(Collections.singletonList(table));
        TableCache.init(properties);
    }

    @AfterEach
    void tearDown() {
        TableCache.reset();
        FieldCryptoServiceHolder.reset();
    }

    @Test
    void decryptsIfnullLabelViaWhitespaceNormalizedMatch() throws Exception {
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parse =
                SecurtkitUtils.parseSql("SELECT IFNULL(phone,'2') FROM user", "default");

        ResultSet rs = fakeResultSet("IFNULL(phone, '2')", "", "ENC:13800138000");
        ResultSet wrapped = ResultSetDecryptingProxy.wrap(
                rs, new HashSet<>(Collections.singletonList("user")), parse,
                "SELECT IFNULL(phone,'2') FROM user", "default");

        assertEquals("13800138000", wrapped.getString(1));
    }

    @Test
    void retainsLiteralDefaultWhenNotCipher() throws Exception {
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parse =
                SecurtkitUtils.parseSql("SELECT IFNULL(phone,'2') AS phone FROM user", "default");

        ResultSet rs = fakeResultSet("phone", "user", "2");
        ResultSet wrapped = ResultSetDecryptingProxy.wrap(
                rs, new HashSet<>(Collections.singletonList("user")), parse,
                "SELECT IFNULL(phone,'2') AS phone FROM user", "default");

        assertEquals("2", wrapped.getString(1));
    }

    @Test
    void barePhoneStillDecrypts() throws Exception {
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parse =
                SecurtkitUtils.parseSql("SELECT phone FROM user", "default");

        ResultSet rs = fakeResultSet("phone", "user", "ENC:13900139000");
        ResultSet wrapped = ResultSetDecryptingProxy.wrap(
                rs, new HashSet<>(Collections.singletonList("user")), parse,
                "SELECT phone FROM user", "default");

        assertEquals("13900139000", wrapped.getString(1));
    }

    private static ResultSet fakeResultSet(final String label, final String table, final String value) {
        final ResultSetMetaData meta = (ResultSetMetaData) Proxy.newProxyInstance(
                ResultSetMetaData.class.getClassLoader(),
                new Class[]{ResultSetMetaData.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        String name = method.getName();
                        if ("getColumnCount".equals(name)) {
                            return 1;
                        }
                        if ("getColumnLabel".equals(name) || "getColumnName".equals(name)) {
                            return label;
                        }
                        if ("getTableName".equals(name)) {
                            return table;
                        }
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class) {
                            return false;
                        }
                        if (rt == int.class) {
                            return 0;
                        }
                        return null;
                    }
                });

        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class[]{ResultSet.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String name = method.getName();
                        if ("getMetaData".equals(name)) {
                            return meta;
                        }
                        if ("getString".equals(name) || "getNString".equals(name) || "getObject".equals(name)) {
                            return value;
                        }
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class) {
                            return false;
                        }
                        if (rt == int.class) {
                            return 0;
                        }
                        if (rt == long.class) {
                            return 0L;
                        }
                        return null;
                    }
                });
    }

    public static class PrefixStrategy implements FieldEncryptorStrategy {
        @Override
        public String encryption(String oldValue) {
            return "ENC:" + oldValue;
        }

        @Override
        public String decryption(String oldValue) {
            if (oldValue != null && oldValue.startsWith("ENC:")) {
                return oldValue.substring(4);
            }
            return oldValue;
        }
    }
}
