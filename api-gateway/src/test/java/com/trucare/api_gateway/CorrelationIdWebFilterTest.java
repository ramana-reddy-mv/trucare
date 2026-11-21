package com.trucare.api_gateway;

import com.trucare.api_gateway.config.CorrelationIdWebFilter;
import com.trucare.shared.observability.CorrelationId;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrelationIdWebFilterTest {

    private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

    @Test
    void acceptsAndPropagatesValidatedCorrelationIdInRequestResponseAndReactorContext() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/").header(CorrelationId.HEADER, "business-request-42").build());
        AtomicReference<String> requestHeader = new AtomicReference<>();
        AtomicReference<String> contextValue = new AtomicReference<>();

        filter.filter(exchange, filteredExchange -> {
            requestHeader.set(filteredExchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER));
            return reactor.core.publisher.Mono.deferContextual(context -> {
                contextValue.set(context.get(CorrelationId.CONTEXT_KEY));
                return reactor.core.publisher.Mono.empty();
            });
        }).block();

        assertEquals("business-request-42", requestHeader.get());
        assertEquals("business-request-42", contextValue.get());
        assertEquals("business-request-42",
                exchange.getResponse().getHeaders().getFirst(CorrelationId.HEADER));
    }

    @Test
    void generatesCorrelationIdWhenHeaderIsAbsent() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());

        filter.filter(exchange, filteredExchange -> reactor.core.publisher.Mono.empty()).block();

        String correlationId = exchange.getResponse().getHeaders().getFirst(CorrelationId.HEADER);
        assertTrue(CorrelationId.isValid(correlationId));
    }

    @Test
    void rejectsUnsafeCorrelationIdAndReturnsGeneratedDiagnosticId() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/").header(CorrelationId.HEADER, "bad value").build());

        filter.filter(exchange, filteredExchange -> reactor.core.publisher.Mono.error(
                new AssertionError("Invalid request must not reach the chain"))).block();

        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
        assertTrue(CorrelationId.isValid(
                exchange.getResponse().getHeaders().getFirst(CorrelationId.HEADER)));
    }
}
