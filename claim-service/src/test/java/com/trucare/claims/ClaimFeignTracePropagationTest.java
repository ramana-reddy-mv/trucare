package com.trucare.claims;

import com.sun.net.httpserver.HttpServer;
import com.trucare.shared.client.MemberClient;
import com.trucare.shared.observability.CorrelationId;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/trucare_claims_test",
        "spring.datasource.username=postgres",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=update",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@AutoConfigureObservability
class ClaimFeignTracePropagationTest {

    private static final AtomicReference<String> downstreamTraceparent = new AtomicReference<>();
    private static final AtomicReference<String> downstreamCorrelationId = new AtomicReference<>();
    private static final HttpServer downstream = startDownstream();

    @Autowired
    private MemberClient memberClient;

    @Autowired
    private Tracer tracer;

    @Autowired
    private Propagator propagator;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.sharedMemberClient.url",
                () -> "http://127.0.0.1:" + downstream.getAddress().getPort());
        TestJwtKeys.configure(registry);
    }

    @AfterAll
    static void stopDownstream() {
        downstream.stop(0);
    }

    @Test
    void openFeignPropagatesActiveTraceAndCorrelationId() {
        assertTrue(propagator.fields().contains("traceparent"),
                () -> "Configured trace propagation fields: " + propagator.fields());
        Span parent = tracer.nextSpan().name("claim-to-member-test").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(parent)) {
            assertEquals(parent.context().traceId(), tracer.currentSpan().context().traceId());
            MDC.put(CorrelationId.MDC_KEY, "claim-business-request-17");
            try {
                assertTrue(memberClient.validateMember("M-TRACE"));
            } finally {
                MDC.remove(CorrelationId.MDC_KEY);
            }
        } finally {
            parent.end();
        }

        assertEquals("claim-business-request-17", downstreamCorrelationId.get());
        String traceparent = downstreamTraceparent.get();
        assertTrue(traceparent != null
                && traceparent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}"),
                () -> "Expected a W3C traceparent but received " + traceparent);
        assertEquals(parent.context().traceId(), traceparent.split("-")[1]);
        assertTrue(!parent.context().spanId().equals(traceparent.split("-")[2]),
                "Feign should create a child client span rather than reusing the parent span");
    }

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/members/validate/", exchange -> {
                downstreamTraceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
                downstreamCorrelationId.set(exchange.getRequestHeaders().getFirst(CorrelationId.HEADER));
                byte[] response = "true".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
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
}
