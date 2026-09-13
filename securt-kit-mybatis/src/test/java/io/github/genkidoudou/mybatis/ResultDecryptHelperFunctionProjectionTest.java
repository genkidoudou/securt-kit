package io.github.genkidoudou.mybatis;

import cn.hutool.core.lang.Pair;
import io.github.genkidoudou.core.crypto.FieldCryptoService;
import io.github.genkidoudou.core.crypto.FieldCryptoServiceHolder;
import io.github.genkidoudou.core.parser.dto.ColumnTableDto;
import io.github.genkidoudou.core.parser.dto.FieldEncryptorInfoDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultDecryptHelperFunctionProjectionTest {

    @BeforeEach
    void setUp() {
        FieldCryptoServiceHolder.reset();
        FieldCryptoServiceHolder.set(new FieldCryptoService() {
            @Override
            public boolean needEncrypt(String table, String column, String datasourceId) {
                return "user".equalsIgnoreCase(table) && "phone".equalsIgnoreCase(column);
            }

            @Override
            public String encrypt(String table, String column, String plain, String datasourceId) {
                return plain == null ? null : "ENC:" + plain;
            }

            @Override
            public String decrypt(String table, String column, String cipher, String datasourceId) {
                if (cipher != null && cipher.startsWith("ENC:")) {
                    return cipher.substring(4);
                }
                return cipher;
            }
        });
    }

    @AfterEach
    void tearDown() {
        FieldCryptoServiceHolder.reset();
    }

    @Test
    void decryptsMapKeyedByExpressionLabel() {
        FieldEncryptorInfoDto mapping = FieldEncryptorInfoDto.builder()
                .columnName("ifnull(phone, '2')")
                .sourceTableName("user")
                .sourceColumn("phone")
                .resultColumnIndex(1)
                .build();
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parse =
                Pair.of(Collections.<String, ColumnTableDto>emptyMap(), Collections.singletonList(mapping));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("IFNULL(phone,'2')", "ENC:13800138000");

        ResultDecryptHelper.decryptResult(Collections.singletonList(row), parse, "default");
        assertEquals("13800138000", row.get("IFNULL(phone,'2')"));
    }

    @Test
    void decryptsEntityBySourcePropertyWhenAliasMissing() {
        FieldEncryptorInfoDto mapping = FieldEncryptorInfoDto.builder()
                .columnName("ifnull(phone,'2')")
                .sourceTableName("user")
                .sourceColumn("phone")
                .build();
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parse =
                Pair.of(Collections.<String, ColumnTableDto>emptyMap(), Collections.singletonList(mapping));

        DemoUser user = new DemoUser();
        user.setPhone("ENC:13900139000");
        ResultDecryptHelper.decryptResult(Collections.singletonList(user), parse, "default");
        assertEquals("13900139000", user.getPhone());
    }

    @Test
    void retainsLiteralDefaultWhenDecryptFailsOpen() {
        FieldEncryptorInfoDto mapping = FieldEncryptorInfoDto.builder()
                .columnName("phone")
                .sourceTableName("user")
                .sourceColumn("phone")
                .build();
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parse =
                Pair.of(Collections.<String, ColumnTableDto>emptyMap(), Collections.singletonList(mapping));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("phone", "2");
        ResultDecryptHelper.decryptResult(Collections.singletonList(row), parse, "default");
        assertEquals("2", row.get("phone"));
    }

    public static class DemoUser {
        private String phone;

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
    }
}
