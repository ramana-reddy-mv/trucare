package com.trucare.api_gateway.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@RestController
public class SwaggerConfigController {
    private static final List<ServiceGroup> SERVICES = List.of(
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
    private final Duration specTimeout;
    private final Duration refreshInterval;
    private final Map<String, JsonNode> specifications = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();
    private final AtomicLong lastRefresh = new AtomicLong();

    public SwaggerConfigController(ObjectProvider<DiscoveryClient> discoveryClientProvider,
                                   WebClient.Builder webClientBuilder,
                                   org.springframework.core.env.Environment environment) {
        this.discoveryClient = discoveryClientProvider.getIfAvailable();
        this.webClient = webClientBuilder.build();
        this.specTimeout = Duration.ofMillis(environment.getProperty("gateway.swagger.response-timeout-ms", Long.class, 1000L));
        this.refreshInterval = Duration.ofMillis(environment.getProperty("gateway.swagger.refresh-interval-ms", Long.class, 15000L));
    }

    @org.springframework.scheduling.annotation.Scheduled(
            initialDelayString = "${gateway.swagger.refresh-interval-ms:15000}",
            fixedDelayString = "${gateway.swagger.refresh-interval-ms:15000}")
    public void scheduledRefresh() {
        refresh().subscribe();
    }

    @GetMapping(value = "/swagger-config", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> swaggerConfig(ServerWebExchange exchange) {
        Mono<Void> ready = lastRefresh.get() == 0 ? refresh() : Mono.empty();
        return ready.then(Mono.fromSupplier(() -> {
            List<Map<String, String>> urls = SERVICES.stream()
                    .filter(service -> specifications.containsKey(service.path()))
                    .map(service -> Map.of("name", service.displayName(), "url", "/openapi/" + service.path()))
                    .toList();
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("configUrl", "/swagger-config");
            config.put("urls.primaryName", "Authentication Service");
            config.put("oauth2RedirectUrl", exchange.getRequest().getURI().resolve("/webjars/swagger-ui/oauth2-redirect.html").toString());
            config.put("urls", urls);
            config.put("validatorUrl", "");
            return config;
        }));
    }

    @GetMapping(value = "/openapi/{service}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<JsonNode> specification(@PathVariable String service) {
        JsonNode spec = specifications.get(service);
        return spec == null ? Mono.empty() : Mono.just(spec);
    }

    private Mono<Void> refresh() {
        if (discoveryClient == null) {
            lastRefresh.set(System.currentTimeMillis());
            return Mono.empty();
        }
        return Flux.fromIterable(SERVICES)
                .flatMap(this::refreshService, 10)
                .then()
                .doFinally(signal -> lastRefresh.set(System.currentTimeMillis()));
    }

    private Mono<Void> refreshService(ServiceGroup service) {
        return Mono.fromCallable(() -> discoveryClient.getInstances(service.serviceId()))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(2))
                .flatMapMany(Flux::fromIterable)
                .flatMap(instance -> fetchSpec(instance).onErrorResume(error -> Mono.empty()))
                .filter(this::validSpec)
                .next()
                .doOnNext(spec -> {
                    specifications.put(service.path(), spec);
                    failures.remove(service.path());
                })
                .switchIfEmpty(Mono.fromRunnable(() -> {
                    int count = failures.computeIfAbsent(service.path(), ignored -> new AtomicInteger()).incrementAndGet();
                    // Tolerate one transient failed probe; exclude after the second consecutive failure.
                    if (count >= 2) specifications.remove(service.path());
                }).then(Mono.empty()))
                .then()
                .onErrorResume(error -> Mono.fromRunnable(() -> {
                    int count = failures.computeIfAbsent(service.path(), ignored -> new AtomicInteger()).incrementAndGet();
                    if (count >= 2) specifications.remove(service.path());
                }));
    }

    private Mono<JsonNode> fetchSpec(ServiceInstance instance) {
        URI uri = URI.create(instance.getUri().toString().replaceAll("/$", "") + "/v3/api-docs");
        return webClient.get().uri(uri).retrieve().bodyToMono(JsonNode.class)
                .timeout(specTimeout)
                .filter(this::validSpec)
                .map(this::useGatewayAsServer);
    }

    private JsonNode useGatewayAsServer(JsonNode source) {
        ObjectNode spec = source.deepCopy();
        ArrayNode servers = JsonNodeFactory.instance.arrayNode();
        servers.add(JsonNodeFactory.instance.objectNode()
                .put("url", "/")
                .put("description", "API Gateway"));
        spec.set("servers", servers);
        return spec;
    }

    private boolean validSpec(JsonNode spec) {
        return spec != null && spec.hasNonNull("openapi") && spec.has("paths");
    }

    private record ServiceGroup(String serviceId, String path, String displayName) { }
}
