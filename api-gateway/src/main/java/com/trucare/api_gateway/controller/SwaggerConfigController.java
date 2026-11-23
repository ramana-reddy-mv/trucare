package com.trucare.api_gateway.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class SwaggerConfigController {
    private static final Duration SPEC_TIMEOUT = Duration.ofMillis(800);
    private static final List<ServiceGroup> SERVICE_GROUPS = List.of(
            new ServiceGroup("auth-service", "auth", "Authentication Service"),
            new ServiceGroup("member-service", "members", "Member Service"),
            new ServiceGroup("provider-service", "providers", "Provider Service"),
            new ServiceGroup("claims-service", "claims", "Claims Service"),
            new ServiceGroup("care-service", "care", "Care Service"),
            new ServiceGroup("document-service", "documents", "Document Service"),
            new ServiceGroup("policy-service", "policies", "Policy Service"),
            new ServiceGroup("settlement-service", "settlements", "Settlement Service"),
            new ServiceGroup("payment-service", "payments", "Payment Service"),
            new ServiceGroup("notification-service", "notifications", "Notification Service"));

    private final DiscoveryClient discoveryClient;
    private final WebClient webClient;

    public SwaggerConfigController(ObjectProvider<DiscoveryClient> discoveryClientProvider,
                                   WebClient.Builder webClientBuilder) {
        this.discoveryClient = discoveryClientProvider.getIfAvailable();
        this.webClient = webClientBuilder.build();
    }

    @GetMapping(value = "/swagger-config", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> swaggerConfig(ServerWebExchange exchange) {
        return Flux.fromIterable(SERVICE_GROUPS)
                .flatMapSequential(this::isAvailable)
                .filter(AvailableGroup::available)
                .map(group -> Map.of("name", group.service().displayName(),
                        "url", "/openapi/" + group.service().path()))
                .collectList()
                .map(urls -> {
                    Map<String, Object> config = new LinkedHashMap<>();
                    config.put("configUrl", "/swagger-config");
                    config.put("oauth2RedirectUrl", exchange.getRequest().getURI()
                            .resolve("/webjars/swagger-ui/oauth2-redirect.html").toString());
                    config.put("urls", urls);
                    config.put("validatorUrl", "");
                    return config;
                });
    }

    private Mono<AvailableGroup> isAvailable(ServiceGroup group) {
        if (discoveryClient == null) {
            return Mono.just(new AvailableGroup(group, false));
        }
        return Mono.fromCallable(() -> discoveryClient.getInstances(group.serviceId()))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofMillis(300))
                .flatMapMany(Flux::fromIterable)
                .flatMap(instance -> fetchSpec(instance).onErrorReturn(false))
                .any(Boolean::booleanValue)
                .onErrorReturn(false)
                .map(available -> new AvailableGroup(group, available));
    }

    private Mono<Boolean> fetchSpec(ServiceInstance instance) {
        URI specUri = URI.create(instance.getUri().toString().replaceAll("/$", "") + "/v3/api-docs");
        return webClient.get().uri(specUri).retrieve().bodyToMono(JsonNode.class)
                .timeout(SPEC_TIMEOUT)
                .map(spec -> spec.hasNonNull("openapi") && spec.has("paths"));
    }

    private record ServiceGroup(String serviceId, String path, String displayName) { }
    private record AvailableGroup(ServiceGroup service, boolean available) { }
}
