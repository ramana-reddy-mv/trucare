package com.trucare.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.env.Environment;
import org.springframework.context.ApplicationContext;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import com.trucare.shared.observability.CorrelationId;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "EUREKA_URL=http://test-eureka:8761/eureka/"
})
@AutoConfigureWebTestClient
class ApiGatewayApplicationTests {
    @Autowired private WebTestClient webTestClient;
    @Autowired private RouteDefinitionLocator routeDefinitionLocator;
    @Autowired private Environment environment;
    @Autowired private ApplicationContext applicationContext;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.configure(registry);
    }

    @Test
    void protectedPathsRejectMissingMalformedAndExpiredTokens() {
        webTestClient.get().uri("/not-routed")
                .exchange().expectStatus().isUnauthorized();
        webTestClient.get().uri("/not-routed")
                .header("Authorization", "Bearer not-a-jwt")
                .exchange().expectStatus().isUnauthorized();

        String expired = TestJwtKeys.token("SERVICE", Instant.now().minusSeconds(120),
                Instant.now().minusSeconds(60));
        webTestClient.get().uri("/not-routed")
                .header("Authorization", "Bearer " + expired)
                .exchange().expectStatus().isUnauthorized();
        String valid = TestJwtKeys.token("MEMBER", Instant.now(), Instant.now().plusSeconds(600));
        String tampered = valid.substring(0, valid.lastIndexOf('.') + 1) + "invalid-signature";
        webTestClient.get().uri("/not-routed")
                .header("Authorization", "Bearer " + tampered)
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void publicHealthPathDoesNotRequireJwtAndValidJwtPassesGatewayValidation() {
        webTestClient.get().uri("/actuator/health")
                .exchange().expectStatus().value(status ->
                        org.junit.jupiter.api.Assertions.assertNotEquals(HttpStatus.UNAUTHORIZED.value(), status));

        String valid = TestJwtKeys.token("MEMBER", Instant.now(), Instant.now().plusSeconds(600));
        webTestClient.get().uri("/not-routed")
                .header("Authorization", "Bearer " + valid)
                .exchange().expectStatus().value(status ->
                        org.junit.jupiter.api.Assertions.assertNotEquals(HttpStatus.UNAUTHORIZED.value(), status));
    }

    @Test
    void correlationIdIsReturnedOrGeneratedAndUnsafeValuesAreRejected() {
        webTestClient.get().uri("/not-routed")
                .header(CorrelationId.HEADER, "business-request-42")
                .exchange()
                .expectHeader().valueEquals(CorrelationId.HEADER, "business-request-42")
                .expectStatus().isUnauthorized();

        webTestClient.get().uri("/not-routed")
                .exchange()
                .expectHeader().value(CorrelationId.HEADER,
                        value -> org.junit.jupiter.api.Assertions.assertTrue(CorrelationId.isValid(value)));

        webTestClient.get().uri("/not-routed")
                .header(CorrelationId.HEADER, "unsafe value")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void onlyPrometheusAndHealthAreExposedActuatorEndpoints() {
        assertTrue(applicationContext.getBeansOfType(PrometheusMeterRegistry.class).size() == 1,
                () -> "Prometheus registry beans: "
                        + applicationContext.getBeansOfType(PrometheusMeterRegistry.class).keySet());
        webTestClient.get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertTrue(body.contains("http_server_requests_seconds")));

        webTestClient.get().uri("/actuator/env")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void everyGatewayRouteUsesARegisteredServiceIdAndEurekaUrlIsExternalized() {
        Set<String> registeredServiceIds = Set.of("auth-service", "member-service", "provider-service",
                "claims-service", "care-service", "document-service", "policy-service",
                "settlement-service", "payment-service", "notification-service");
        var routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();

        assertTrue(routes != null && !routes.isEmpty());
        for (RouteDefinition route : routes) {
            assertEquals("lb", route.getUri().getScheme(), route.getId());
            assertTrue(registeredServiceIds.contains(route.getUri().getHost()),
                    () -> route.getId() + " targets unregistered service " + route.getUri());
        }
        assertTrue(routes.stream().anyMatch(route -> route.getId().equals("care-api")
                && route.getUri().getHost().equals("care-service")));
        assertEquals("http://test-eureka:8761/eureka/",
                environment.getProperty("eureka.client.service-url.defaultZone"));
    }

}
