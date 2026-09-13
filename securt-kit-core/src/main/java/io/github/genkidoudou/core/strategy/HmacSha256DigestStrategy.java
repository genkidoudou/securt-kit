package io.github.genkidoudou.core.strategy;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class HmacSha256DigestStrategy implements FieldEncryptorStrategy {

    private final byte[] keyBytes;

    public HmacSha256DigestStrategy(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("digest-hmac-key must not be blank");
        }
        this.keyBytes = key.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String encryption(String oldValue) {
        throw new UnsupportedOperationException("HmacSha256DigestStrategy is digest-only");
    }

    @Override
    public String decryption(String oldValue) {
        throw new UnsupportedOperationException("HmacSha256DigestStrategy is digest-only");
    }

    @Override
    public boolean supportsDigest() {
        return true;
    }

    @Override
    public String digest(Map<String, String> sourcePlainValues) {
        if (sourcePlainValues == null || sourcePlainValues.isEmpty()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : sourcePlainValues.entrySet()) {
                sb.append(e.getKey()).append('=').append(e.getValue() == null ? "" : e.getValue()).append('\n');
            }
            byte[] raw = mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC digest failed", ex);
        }
    }
}
