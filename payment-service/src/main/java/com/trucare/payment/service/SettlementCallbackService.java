package com.trucare.payment.service;

import com.trucare.payment.client.*;
import com.trucare.payment.exception.PaymentException;
import com.trucare.payment.gateway.PaymentGatewayResult;
import com.trucare.payment.model.*;
import com.trucare.payment.repository.CallbackDeliveryRepository;
import com.trucare.payment.repository.PaymentTransactionRepository;
import com.trucare.payment.repository.PaymentRepository;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Slf4j
public class SettlementCallbackService {
    private final SettlementClient settlementClient;
    private final PaymentStore store;
    private final CallbackDeliveryRepository callbacks;
    private final PaymentGatewayProcessor gatewayProcessor;
    private final int maxAttempts;
    private final long initialDelayMs;

    public SettlementCallbackService(SettlementClient settlementClient, PaymentStore store,
                                     CallbackDeliveryRepository callbacks,
                                     PaymentGatewayProcessor gatewayProcessor,
                                     @Value("${payment.callback.retry.max-attempts:5}") int maxAttempts,
                                     @Value("${payment.callback.retry.initial-delay-ms:1000}") long initialDelayMs) {
        this.settlementClient = settlementClient;
        this.store = store;
        this.callbacks = callbacks;
        this.gatewayProcessor = gatewayProcessor;
        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
    }

    public void enqueueAndDeliver(Long paymentId, CallbackTargetStatus target, String remarks,
                                  boolean startGatewayAfterDelivery) {
        CallbackDelivery delivery = store.queueCallback(paymentId, target, remarks,
                startGatewayAfterDelivery, maxAttempts, Instant.now());
        deliver(delivery.getId());
    }

    public void deliver(Long callbackId) {
        CallbackDelivery delivery = store.claimCallback(callbackId);
        if (delivery == null) {
            return;
        }
        try {
            SettlementResponse settlement = settlementClient.getSettlement(delivery.getPayment().getSettlementId());
            if (isAlreadyAppliedOrApply(settlement, delivery)) {
                store.callbackDelivered(callbackId);
                if (delivery.isStartGatewayAfterDelivery()) {
                    resumeGatewayContinuation(callbackId);
                }
            } else {
                store.callbackFailed(callbackId, nextAttempt(delivery));
            }
        } catch (FeignException exception) {
            log.warn("Settlement callback delivery failed for paymentId={} attempt={}",
                    delivery.getPayment().getId(), delivery.getAttemptCount());
            store.callbackFailed(callbackId, nextAttempt(delivery));
        }
    }

    public void resumeGatewayContinuation(Long callbackId) {
        gatewayProcessor.processCallbackContinuation(callbackId);
    }

    public void retryDead(Long paymentId) {
        for (CallbackDelivery delivery : callbacks.findByPaymentIdAndDeliveryStatus(paymentId, CallbackStatus.DEAD)) {
            delivery.setDeliveryStatus(CallbackStatus.PENDING);
            delivery.setAttemptCount(0);
            delivery.setNextAttemptAt(Instant.now());
            callbacks.save(delivery);
        }
    }

    private boolean isAlreadyAppliedOrApply(SettlementResponse settlement, CallbackDelivery delivery) {
        CallbackTargetStatus target = delivery.getTargetStatus();
        String current = settlement.settlementStatus();
        if (target.name().equals(current)) {
            return true;
        }
        if (target == CallbackTargetStatus.PAYMENT_PROCESSING
                && ("SETTLED".equals(current) || "FAILED".equals(current))) {
            return true;
        }
        boolean canApply = target == CallbackTargetStatus.PAYMENT_PROCESSING
                ? "READY_FOR_PAYMENT".equals(current)
                : "PAYMENT_PROCESSING".equals(current);
        if (!canApply) {
            return false;
        }
        settlementClient.updatePaymentStatus(settlement.id(),
                new SettlementPaymentStatusRequest(target.name(),
                        delivery.getPayment().getPaymentReference(), delivery.getRemarks()));
        return true;
    }

    private Instant nextAttempt(CallbackDelivery delivery) {
        long multiplier = 1L << Math.min(Math.max(delivery.getAttemptCount() - 1, 0), 10);
        return Instant.now().plusMillis(initialDelayMs * multiplier);
    }
}
