package com.trucare.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
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
import java.util.List;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import org.springframework.boot.test.mock.mockito.MockBean;

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
    @MockBean private DiscoveryClient discoveryClient;

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
        assertTrue(routes.stream().anyMatch(route -> route.getId().equals("claims-health")
                && route.getUri().getHost().equals("claims-service")));
        assertTrue(routes.stream().anyMatch(route -> route.getId().equals("members-health")
                && route.getUri().getHost().equals("member-service")));
        assertTrue(routes.stream().noneMatch(route -> route.getId().endsWith("-openapi")),
                "OpenAPI docs are served from the availability-aware cache");
        assertEquals("http://test-eureka:8761/eureka/",
                environment.getProperty("eureka.client.service-url.defaultZone"));
    }

    @Test
    void swaggerUiIsAvailableWithoutChangingBusinessApiAuthentication() {
        webTestClient.get().uri("/swagger-ui.html")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals("Location", "/webjars/swagger-ui/index.html");
        webTestClient.get().uri("/webjars/swagger-ui/index.html")
                .exchange()
                .expectStatus().isOk();
        webTestClient.get().uri("/api/claims")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void swaggerConfigurationIncludesClaimsAlongsideAuthWhenBothAreAvailable() throws Exception {
        HttpServer healthyService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        healthyService.createContext("/v3/api-docs", exchange -> {
            byte[] spec = "{\"openapi\":\"3.0.1\",\"paths\":{}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, spec.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(spec);
            }
        });
        healthyService.start();
        when(discoveryClient.getInstances("auth-service")).thenReturn(List.of(
                new DefaultServiceInstance("auth-test", "auth-service", "127.0.0.1",
                        healthyService.getAddress().getPort(), false)));
        when(discoveryClient.getInstances("claims-service")).thenReturn(List.of(
                new DefaultServiceInstance("claims-test", "claims-service", "127.0.0.1",
                        healthyService.getAddress().getPort(), false)));
        when(discoveryClient.getInstances("member-service")).thenReturn(List.of(
                new DefaultServiceInstance("member-test", "member-service", "127.0.0.1", 1, false)));
        try {
        webTestClient.get().uri("/swagger-config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.urls").isArray()
                .jsonPath("$.urls.length()").isEqualTo(2)
                .jsonPath("$.urls[0].url").isEqualTo("/openapi/auth")
                .jsonPath("$.urls[1].name").isEqualTo("Claims Service")
                .jsonPath("$.urls[1].url").isEqualTo("/openapi/claims")
                .jsonPath("$['urls.primaryName']").isEqualTo("Authentication Service")
                .jsonPath("$.configUrl").isEqualTo("/swagger-config");

        webTestClient.get().uri("/openapi/auth").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.servers[0].url").isEqualTo("/");
        webTestClient.get().uri("/openapi/claims").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.servers[0].url").isEqualTo("/");
        } finally {
            healthyService.stop(0);
        }
    }

}
