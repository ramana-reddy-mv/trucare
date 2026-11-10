package com.trucare.auth_service;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

final class TestJwtKeys {
    private static final KeyPair KEYS = generate();

    private TestJwtKeys() {
    }

    static void configure(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.public-key-base64",
                () -> Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded()));
        registry.add("security.jwt.private-key-base64",
                () -> Base64.getEncoder().encodeToString(KEYS.getPrivate().getEncoded()));
    }

    static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Could not generate test JWT keys", exception);
        }
    }
}
