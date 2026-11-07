package com.trucare.payment.service;

import com.trucare.payment.dto.PaymentResponse;
import com.trucare.payment.exception.PaymentException;
import com.trucare.payment.gateway.*;
import com.trucare.payment.model.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayProcessor {
    private final PaymentGateway gateway;
    private final PaymentStore store;
    private final ObjectProvider<SettlementCallbackService> callbackService;

    public PaymentGatewayProcessor(PaymentGateway gateway, PaymentStore store,
                                   ObjectProvider<SettlementCallbackService> callbackService) {
        this.gateway = gateway;
        this.store = store;
        this.callbackService = callbackService;
    }

    public PaymentResponse processCallbackContinuation(Long callbackId) {
        PaymentStore.GatewayAttempt gatewayAttempt = store.beginAttemptFromCallback(callbackId);
        if (gatewayAttempt == null) {
            return null;
        }
        Long paymentId = gatewayAttempt.paymentId();
        PaymentTransaction attempt = gatewayAttempt.transaction();
        Payment payment = store.getEntity(paymentId);
        PaymentGatewayResult result;
        try {
            result = gateway.process(payment, attempt.getTransactionReference(), payment.getPaymentReference());
        } catch (PaymentGatewayUnavailableException exception) {
            result = new PaymentGatewayResult(GatewayOutcome.TIMEOUT, null,
                    "PROVIDER_UNAVAILABLE", "Payment provider outcome is unknown");
        }
        PaymentStore.AppliedGatewayResult applied =
                store.applyGatewayResult(paymentId, attempt.getId(), result);
        if (applied.callbackId() != null) {
            callbackService.getObject().deliver(applied.callbackId());
        }
        return applied.response();
    }

    public PaymentResponse reconcile(Long paymentId) {
        Payment payment = store.getEntity(paymentId);
        if (payment.getPaymentStatus() != PaymentStatus.PROCESSING) {
            throw new PaymentException(HttpStatus.CONFLICT, "Only processing payments can be reconciled");
        }
        PaymentTransaction attempt = store.getLatestTransaction(paymentId);
        String providerLookupReference = attempt.getProviderReference() != null
                ? attempt.getProviderReference() : attempt.getTransactionReference();
        PaymentGatewayResult result = gateway.query(providerLookupReference);
        PaymentStore.AppliedGatewayResult applied =
                store.applyGatewayResult(paymentId, attempt.getId(), result);
        if (applied.callbackId() != null) {
            callbackService.getObject().deliver(applied.callbackId());
        }
        return applied.response();
    }
}
