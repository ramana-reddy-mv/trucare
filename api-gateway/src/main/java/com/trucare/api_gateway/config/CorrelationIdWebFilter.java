package com.trucare.api_gateway.config;

import com.trucare.shared.observability.CorrelationId;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        List<String> values = exchange.getRequest().getHeaders().get(CorrelationId.HEADER);
        if (values != null && (values.size() != 1 || !CorrelationId.isValid(values.get(0)))) {
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            exchange.getResponse().getHeaders().set(CorrelationId.HEADER, CorrelationId.generate());
            return exchange.getResponse().setComplete();
        }

        String correlationId = values == null ? CorrelationId.generate() : values.get(0);
        exchange.getResponse().getHeaders().set(CorrelationId.HEADER, correlationId);
        ServerWebExchange correlatedExchange = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(CorrelationId.HEADER, correlationId)))
                .build();
        return chain.filter(correlatedExchange)
                .contextWrite(context -> context.put(CorrelationId.CONTEXT_KEY, correlationId));
    }
}
