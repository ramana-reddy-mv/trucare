package com.trucare.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "TRUCARE_JWT_SECRET_BASE64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureWebTestClient
class ApiGatewayApplicationTests {
    @Autowired private WebTestClient webTestClient;
    @Autowired private JwtEncoder jwtEncoder;

    @Test
    void protectedPathsRejectMissingMalformedAndExpiredTokens() {
        webTestClient.get().uri("/not-routed")
                .exchange().expectStatus().isUnauthorized();
        webTestClient.get().uri("/not-routed")
                .header("Authorization", "Bearer not-a-jwt")
                .exchange().expectStatus().isUnauthorized();

        String expired = token("SERVICE", Instant.now().minusSeconds(120),
                Instant.now().minusSeconds(60));
        webTestClient.get().uri("/not-routed")
                .header("Authorization", "Bearer " + expired)
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void publicHealthPathDoesNotRequireJwtAndValidJwtPassesGatewayValidation() {
        webTestClient.get().uri("/actuator/health")
                .exchange().expectStatus().value(status ->
                        org.junit.jupiter.api.Assertions.assertNotEquals(HttpStatus.UNAUTHORIZED.value(), status));

        String valid = token("MEMBER", Instant.now(), Instant.now().plusSeconds(600));
        webTestClient.get().uri("/not-routed")
                .header("Authorization", "Bearer " + valid)
                .exchange().expectStatus().value(status ->
                        org.junit.jupiter.api.Assertions.assertNotEquals(HttpStatus.UNAUTHORIZED.value(), status));
    }

    private String token(String role, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("trucare-auth-service")
                .subject("test-user")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of(role))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
