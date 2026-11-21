package com.trucare.api_gateway.config;

import com.trucare.shared.observability.CorrelationId;
import jakarta.annotation.PostConstruct;
import io.micrometer.context.ContextRegistry;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;

@Configuration
class CorrelationIdContextConfiguration {

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
}
