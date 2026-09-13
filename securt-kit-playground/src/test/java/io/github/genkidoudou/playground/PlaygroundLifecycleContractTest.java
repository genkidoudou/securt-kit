package io.github.genkidoudou.playground;

import cn.hutool.json.JSONUtil;
import io.github.genkidoudou.playground.dto.LifecycleRequest;
import io.github.genkidoudou.playground.dto.LifecycleSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaygroundLifecycleContractTest {

    @Test
    void lifecycleRequestCarriesStatelessOperationContext() {
        LifecycleRequest request = new LifecycleRequest();
        request.setDatasourceId("primary");
        request.setRecordId(42L);
        request.setName("张三");
        request.setPhone("13800138000");
        request.setAge(28);
        request.setEmail("demo@example.com");
        request.setTamperTarget("row_digest");

        assertEquals("primary", request.getDatasourceId());
        assertEquals(42L, request.getRecordId());
        assertEquals("row_digest", request.getTamperTarget());
    }

    @Test
    void lifecycleSnapshotSerializesEvidenceAssertionsAndError() {
        LifecycleSnapshot snapshot = new LifecycleSnapshot();
        snapshot.setStep("insert");
        snapshot.setStatus("FAILED");
        snapshot.setRecordId(42L);
        snapshot.setRequestPlaintext(Collections.<String, Object>singletonMap("phone", "13800138000"));
        snapshot.setRawDatabaseRow(Collections.<String, Object>singletonMap("phone", "ciphertext"));
        snapshot.setBusinessRow(Collections.<String, Object>singletonMap("phone", "13800138000"));

        LifecycleSnapshot.Assertion assertion = new LifecycleSnapshot.Assertion(
                "phone-encrypted", false, "明文不同于密文", "值相同", "未观察到加密效果");
        snapshot.setAssertions(Collections.singletonList(assertion));
        snapshot.setDigestVerification(new LifecycleSnapshot.DigestVerification(
                "FAILED", Collections.singletonList("phone"), "row_digest", "摘要不匹配"));
        snapshot.setError(new LifecycleSnapshot.ErrorDetail(
                "DIGEST_VERIFICATION", "摘要校验失败"));

        String json = JSONUtil.toJsonStr(snapshot);
        assertTrue(json.contains("\"requestPlaintext\""));
        assertTrue(json.contains("\"rawDatabaseRow\""));
        assertTrue(json.contains("\"businessRow\""));
        assertTrue(json.contains("\"phone-encrypted\""));
        assertTrue(json.contains("\"digestVerification\""));
        assertTrue(json.contains("\"DIGEST_VERIFICATION\""));
    }
}
