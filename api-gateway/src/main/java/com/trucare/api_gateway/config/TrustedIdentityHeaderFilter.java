package com.trucare.api_gateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class TrustedIdentityHeaderFilter implements GlobalFilter, Ordered {
    private static final List<String> CLIENT_CONTROLLED_IDENTITY_HEADERS = List.of(
            "X-Authenticated-User", "X-Authenticated-Roles", "X-User-Id", "X-User-Name",
            "X-User-Roles", "X-Auth-User", "X-Auth-Roles", "X-Roles");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange.mutate().request(request -> request.headers(headers ->
                CLIENT_CONTROLLED_IDENTITY_HEADERS.forEach(headers::remove))).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
