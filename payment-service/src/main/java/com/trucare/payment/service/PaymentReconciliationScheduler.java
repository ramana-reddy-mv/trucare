package com.trucare.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(name = "payment.reconciliation.enabled", havingValue = "true")
public class PaymentReconciliationScheduler {
    private final PaymentStore store;
    private final PaymentGatewayProcessor processor;

    public PaymentReconciliationScheduler(PaymentStore store, PaymentGatewayProcessor processor) {
        this.store = store;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${payment.reconciliation.fixed-delay-ms:60000}")
    public void reconcileProcessingPayments() {
        store.processingPaymentIds(PageRequest.of(0, 25)).forEach(id -> {
            try {
                processor.reconcile(id);
            } catch (RuntimeException exception) {
                log.warn("Reconciliation deferred for paymentId={}", id);
            }
        });
    }
}
