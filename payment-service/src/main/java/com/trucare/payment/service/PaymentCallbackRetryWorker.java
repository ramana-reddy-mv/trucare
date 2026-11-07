package com.trucare.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
@ConditionalOnProperty(name = "payment.callback.retry.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentCallbackRetryWorker {
    private final PaymentStore store;
    private final SettlementCallbackService callbackService;
    private final long staleDeliveryTimeoutMs;

    public PaymentCallbackRetryWorker(PaymentStore store, SettlementCallbackService callbackService,
                                      @Value("${payment.callback.retry.stale-delivery-timeout-ms:60000}")
                                      long staleDeliveryTimeoutMs) {
        this.store = store;
        this.callbackService = callbackService;
        this.staleDeliveryTimeoutMs = staleDeliveryTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${payment.callback.retry.fixed-delay-ms:5000}")
    public void retryDueCallbacks() {
        Instant cutoff = Instant.now().minusMillis(staleDeliveryTimeoutMs);
        for (Long callbackId : store.staleCallbackIds(cutoff, 20)) {
            store.recoverStaleCallback(callbackId, cutoff);
        }
        for (Long callbackId : store.dueCallbackIds(20)) {
            callbackService.deliver(callbackId);
        }
        for (Long callbackId : store.gatewayContinuationIds(20)) {
            callbackService.resumeGatewayContinuation(callbackId);
        }
    }
}
