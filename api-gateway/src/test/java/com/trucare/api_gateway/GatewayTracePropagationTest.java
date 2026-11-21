package com.trucare.api_gateway;

import com.sun.net.httpserver.HttpServer;
import com.trucare.shared.observability.CorrelationId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@AutoConfigureObservability
@Import(GatewayTracePropagationTest.TestRouteConfiguration.class)
class GatewayTracePropagationTest {

    private static final String PARENT_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String INBOUND_TRACEPARENT =
            "00-" + PARENT_TRACE_ID + "-00f067aa0ba902b7-01";
    private static final AtomicReference<String> downstreamTraceparent = new AtomicReference<>();
    private static final AtomicReference<String> downstreamCorrelationId = new AtomicReference<>();
    private static final HttpServer downstream = startDownstream();

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("trace-test.downstream-port", () -> downstream.getAddress().getPort());
        TestJwtKeys.configure(registry);
    }

    @AfterAll
    static void stopDownstream() {
        downstream.stop(0);
    }

    @Test
    void forwardsW3cTraceContextAndCorrelationSeparatelyToDownstream() {
        webTestClient.get().uri("/observability-test")
                .header("Authorization", "Bearer " + TestJwtKeys.token(
                        "SERVICE", java.time.Instant.now(), java.time.Instant.now().plusSeconds(600)))
                .header("traceparent", INBOUND_TRACEPARENT)
                .header(CorrelationId.HEADER, "gateway-business-request-42")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(CorrelationId.HEADER, "gateway-business-request-42");

        assertEquals("gateway-business-request-42", downstreamCorrelationId.get());
        assertTrue(downstreamTraceparent.get() != null
                && downstreamTraceparent.get().matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}"),
                () -> "Expected a W3C traceparent but received " + downstreamTraceparent.get());
        assertEquals(PARENT_TRACE_ID, downstreamTraceparent.get().split("-")[1]);
        assertTrue(!"00f067aa0ba902b7".equals(downstreamTraceparent.get().split("-")[2]),
                "Gateway should create an outbound client span rather than only forwarding the inbound span");
    }

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/observability-test", exchange -> {
                downstreamTraceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
                downstreamCorrelationId.set(exchange.getRequestHeaders().getFirst(CorrelationId.HEADER));
                byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                try (var body = exchange.getResponseBody()) {
                    body.write(response);
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestRouteConfiguration {

        @Bean
        RouteLocator observabilityTestRoute(
                RouteLocatorBuilder builder,
                @Value("${trace-test.downstream-port}") int downstreamPort) {
            return builder.routes()
                    .route("observability-test", route -> route.path("/observability-test")
                            .uri("http://127.0.0.1:" + downstreamPort))
                    .build();
        }
    }
}
