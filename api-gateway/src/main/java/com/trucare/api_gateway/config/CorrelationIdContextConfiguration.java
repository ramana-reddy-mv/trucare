package com.trucare.api_gateway.config;

import com.trucare.shared.observability.CorrelationId;
import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
class CorrelationIdContextConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(CorrelationIdContextConfiguration.class);

    @PostConstruct
    void registerCorrelationIdMdcAccessor() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(
                CorrelationId.CONTEXT_KEY,
                () -> MDC.get(CorrelationId.MDC_KEY),
                value -> {
                    if (value == null) {
                        MDC.remove(CorrelationId.MDC_KEY);
                    } else {
                        MDC.put(CorrelationId.MDC_KEY, value);
                    }
                },
                () -> MDC.remove(CorrelationId.MDC_KEY));
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    GlobalFilter traceContextRequestLoggingFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            if (request.getURI().getPath().startsWith("/actuator/")) {
                return chain.filter(exchange);
            }

            return Mono.deferContextual(context -> {
                logger.atInfo()
                        .addKeyValue("httpMethod", request.getMethod())
                        .log("Gateway request routed");
                return chain.filter(exchange);
            });
        };
    }
}
