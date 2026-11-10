package com.trucare.api_gateway;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

final class TestJwtKeys {
    private static final KeyPair KEYS = generate();

    private TestJwtKeys() {
    }

    static void configure(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.public-key-base64", TestJwtKeys::publicKey);
    }

    static String token(String role, Instant issuedAt, Instant expiresAt) {
        String header = encode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String payload = encode("{\"iss\":\"trucare-auth-service\",\"sub\":\"test-user\","
                + "\"aud\":[\"trucare-api\"],\"iat\":" + issuedAt.getEpochSecond()
                + ",\"exp\":" + expiresAt.getEpochSecond() + ",\"roles\":[\"" + role + "\"]}");
        String content = header + "." + payload;
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(KEYS.getPrivate());
            signer.update(content.getBytes(StandardCharsets.US_ASCII));
            return content + "." + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(signer.sign());
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Could not sign test JWT", exception);
        }
    }

    private static String publicKey() {
        return Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded());
    }

    private static String encode(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Could not generate test JWT keys", exception);
        }
    }
}
