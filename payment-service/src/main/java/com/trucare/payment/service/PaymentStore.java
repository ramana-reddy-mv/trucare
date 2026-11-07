package com.trucare.payment.service;

import com.trucare.payment.dto.PaymentResponse;
import com.trucare.payment.exception.PaymentException;
import com.trucare.payment.gateway.GatewayOutcome;
import com.trucare.payment.gateway.PaymentGatewayResult;
import com.trucare.payment.mapper.PaymentMapper;
import com.trucare.payment.model.*;
import com.trucare.payment.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;

@Service
public class PaymentStore {
    private final PaymentRepository payments;
    private final PaymentTransactionRepository transactions;
    private final PaymentAuditRepository audits;
    private final CallbackDeliveryRepository callbacks;
    private final PaymentMapper mapper;
    private final int callbackMaxAttempts;
    private final long callbackInitialDelayMs;

    public PaymentStore(PaymentRepository payments, PaymentTransactionRepository transactions,
                        PaymentAuditRepository audits, CallbackDeliveryRepository callbacks,
                        PaymentMapper mapper,
                        @Value("${payment.callback.retry.max-attempts:5}") int callbackMaxAttempts,
                        @Value("${payment.callback.retry.initial-delay-ms:1000}") long callbackInitialDelayMs) {
        this.payments = payments;
        this.transactions = transactions;
        this.audits = audits;
        this.callbacks = callbacks;
        this.mapper = mapper;
        this.callbackMaxAttempts = callbackMaxAttempts;
        this.callbackInitialDelayMs = callbackInitialDelayMs;
    }

    @Transactional
    public CreatedPayment create(Payment payment) {
        Payment saved = payments.saveAndFlush(payment);
        audit(saved, null, PaymentStatus.INITIATED, "PAYMENT_CREATED", "Payment initiated");
        CallbackDelivery callback = newCallback(saved, CallbackTargetStatus.PAYMENT_PROCESSING,
                "Payment processing initiated", true);
        return new CreatedPayment(mapper.toResponse(saved), callback.getId());
    }

    @Transactional
    public GatewayAttempt beginAttemptFromCallback(Long callbackId) {
        CallbackDelivery delivery = callbacks.findByIdForUpdate(callbackId);
        if (delivery == null || delivery.getDeliveryStatus() != CallbackStatus.DELIVERED
                || !delivery.isStartGatewayAfterDelivery()) {
            return null;
        }
        Payment payment = lock(delivery.getPayment().getId());
        delivery.setStartGatewayAfterDelivery(false);
        callbacks.save(delivery);
        if (payment.getPaymentStatus() != PaymentStatus.INITIATED) {
            return null;
        }
        PaymentStatus previous = payment.getPaymentStatus();
        payment.setPaymentStatus(PaymentStatus.PROCESSING);
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPayment(payment);
        transaction.setAttemptNumber(payment.getTransactions().size() + 1);
        transaction.setTransactionReference(payment.getPaymentReference() + "-A" + transaction.getAttemptNumber());
        transaction.setAmount(payment.getAmount());
        transaction.setTransactionStatus(PaymentTransactionStatus.INITIATED);
        payment.getTransactions().add(transaction);
        audit(payment, previous, PaymentStatus.PROCESSING, "PROCESSING_STARTED",
                "Payment attempt " + transaction.getAttemptNumber() + " started");
        payments.saveAndFlush(payment);
        return new GatewayAttempt(payment.getId(), transaction);
    }

    @Transactional
    public Long beginRetry(Long paymentId) {
        Payment payment = lock(paymentId);
        if (payment.getPaymentStatus() != PaymentStatus.FAILED) {
            throw new PaymentException(HttpStatus.CONFLICT, "Only failed payments can be retried");
        }
        PaymentTransaction last = transactions.findTopByPaymentIdOrderByAttemptNumberDesc(paymentId)
                .orElseThrow(() -> new PaymentException(HttpStatus.CONFLICT, "Payment attempt history is missing"));
        if (last.getTransactionStatus() != PaymentTransactionStatus.FAILED) {
            throw new PaymentException(HttpStatus.CONFLICT,
                    "Payment outcome is not a definitive failure and cannot be retried");
        }
        PaymentStatus previous = payment.getPaymentStatus();
        payment.setPaymentStatus(PaymentStatus.INITIATED);
        payment.setFailureCode(null);
        payment.setFailureReason(null);
        audit(payment, previous, PaymentStatus.INITIATED, "AUTHORIZED_RETRY",
                "Authorized retry for the existing payment");
        payments.saveAndFlush(payment);
        CallbackDelivery callback = newCallback(payment, CallbackTargetStatus.PAYMENT_PROCESSING,
                "Authorized payment retry", true);
        return callback.getId();
    }

    @Transactional
    public AppliedGatewayResult applyGatewayResult(Long paymentId, Long transactionId,
                                                   PaymentGatewayResult result) {
        Payment payment = lock(paymentId);
        PaymentTransaction transaction = transactions.findById(transactionId)
                .orElseThrow(() -> new PaymentException(HttpStatus.NOT_FOUND, "Payment transaction was not found"));
        if (!transaction.getPayment().getId().equals(paymentId)) {
            throw new PaymentException(HttpStatus.CONFLICT, "Payment transaction does not belong to payment");
        }
        if (transaction.getTransactionStatus() != PaymentTransactionStatus.INITIATED
                && transaction.getTransactionStatus() != PaymentTransactionStatus.PROCESSING) {
            return new AppliedGatewayResult(mapper.toResponse(payment), null);
        }
        transaction.setProviderReference(result.providerReference());
        transaction.setResponseTimestamp(Instant.now());
        transaction.setFailureCode(result.failureCode());
        transaction.setFailureReason(result.failureReason());

        PaymentStatus previous = payment.getPaymentStatus();
        switch (result.outcome()) {
            case SUCCESS -> {
                transaction.setTransactionStatus(PaymentTransactionStatus.SUCCESS);
                payment.setPaymentStatus(PaymentStatus.SUCCESS);
                payment.setProviderTransactionReference(result.providerReference());
                payment.setFailureCode(null);
                payment.setFailureReason(null);
                payment.setCompletedAt(Instant.now());
                audit(payment, previous, PaymentStatus.SUCCESS, "PAYMENT_SUCCEEDED",
                        "Payment gateway reported success");
            }
            case FAILED -> {
                transaction.setTransactionStatus(PaymentTransactionStatus.FAILED);
                payment.setPaymentStatus(PaymentStatus.FAILED);
                payment.setProviderTransactionReference(result.providerReference());
                payment.setFailureCode(result.failureCode());
                payment.setFailureReason(result.failureReason());
                payment.setCompletedAt(Instant.now());
                audit(payment, previous, PaymentStatus.FAILED, "PAYMENT_FAILED",
                        result.failureReason());
            }
            case TIMEOUT -> {
                transaction.setTransactionStatus(PaymentTransactionStatus.UNKNOWN);
                payment.setFailureCode(result.failureCode());
                payment.setFailureReason(result.failureReason());
            }
            case PROCESSING -> {
                transaction.setTransactionStatus(PaymentTransactionStatus.PROCESSING);
                payment.setProviderTransactionReference(result.providerReference());
            }
        }
        transactions.save(transaction);
        Payment saved = payments.save(payment);
        Long callbackId = null;
        if (result.outcome() == GatewayOutcome.SUCCESS) {
            callbackId = newCallback(saved, CallbackTargetStatus.SETTLED, "Payment completed", false).getId();
        } else if (result.outcome() == GatewayOutcome.FAILED) {
            callbackId = newCallback(saved, CallbackTargetStatus.FAILED, result.failureReason(), false).getId();
        }
        return new AppliedGatewayResult(mapper.toResponse(saved), callbackId);
    }

    @Transactional
    public CallbackDelivery queueCallback(Long paymentId, CallbackTargetStatus target,
                                          String remarks, boolean startGateway, int maxAttempts,
                                          Instant firstAttempt) {
        Payment payment = lock(paymentId);
        return callbacks.save(newCallback(payment, target, remarks, startGateway));
    }

    @Transactional
    public CallbackDelivery claimCallback(Long id) {
        CallbackDelivery delivery = callbacks.findByIdForUpdate(id);
        if (delivery == null || delivery.getDeliveryStatus() != CallbackStatus.PENDING) {
            return null;
        }
        delivery.setDeliveryStatus(CallbackStatus.DELIVERING);
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        delivery.setDeliveryStartedAt(Instant.now());
        return callbacks.saveAndFlush(delivery);
    }

    @Transactional
    public void callbackDelivered(Long id) {
        CallbackDelivery delivery = callbacks.findByIdForUpdate(id);
        delivery.setDeliveryStatus(CallbackStatus.DELIVERED);
        delivery.setDeliveredAt(Instant.now());
        delivery.setNextAttemptAt(null);
        delivery.setDeliveryStartedAt(null);
        callbacks.save(delivery);
    }

    @Transactional
    public void callbackFailed(Long id, Instant nextAttemptAt) {
        CallbackDelivery delivery = callbacks.findByIdForUpdate(id);
        delivery.setNextAttemptAt(nextAttemptAt);
        delivery.setDeliveryStatus(delivery.getAttemptCount() >= delivery.getMaxAttempts()
                ? CallbackStatus.DEAD : CallbackStatus.PENDING);
        delivery.setDeliveryStartedAt(null);
        callbacks.save(delivery);
    }

    @Transactional
    public void recoverStaleCallback(Long id, Instant cutoff) {
        CallbackDelivery delivery = callbacks.findByIdForUpdate(id);
        if (delivery == null || delivery.getDeliveryStatus() != CallbackStatus.DELIVERING
                || delivery.getDeliveryStartedAt() == null
                || delivery.getDeliveryStartedAt().isAfter(cutoff)) {
            return;
        }
        delivery.setDeliveryStatus(delivery.getAttemptCount() >= delivery.getMaxAttempts()
                ? CallbackStatus.DEAD : CallbackStatus.PENDING);
        delivery.setDeliveryStartedAt(null);
        delivery.setNextAttemptAt(Instant.now());
        callbacks.save(delivery);
    }

    @Transactional
    public PaymentResponse get(Long id) {
        return mapper.toResponse(payments.findById(id)
                .orElseThrow(() -> new PaymentException(HttpStatus.NOT_FOUND, "Payment was not found")));
    }

    @Transactional
    public PaymentResponse getByReference(String reference) {
        return mapper.toResponse(payments.findByPaymentReference(reference)
                .orElseThrow(() -> new PaymentException(HttpStatus.NOT_FOUND, "Payment was not found")));
    }

    @Transactional
    public PaymentResponse getBySettlement(Long settlementId) {
        return mapper.toResponse(payments.findBySettlementId(settlementId)
                .orElseThrow(() -> new PaymentException(HttpStatus.NOT_FOUND, "Payment was not found")));
    }

    @Transactional(readOnly = true)
    public Payment getEntity(Long id) {
        return payments.findById(id)
                .orElseThrow(() -> new PaymentException(HttpStatus.NOT_FOUND, "Payment was not found"));
    }

    @Transactional(readOnly = true)
    public Payment getByKey(String key) {
        return payments.findByIdempotencyKey(key).orElse(null);
    }

    @Transactional(readOnly = true)
    public ExistingPayment getExistingByKey(String key) {
        return payments.findByIdempotencyKey(key)
                .map(p -> new ExistingPayment(p.getRequestHash(), mapper.toResponse(p)))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PaymentTransaction getLatestTransaction(Long paymentId) {
        return transactions.findTopByPaymentIdOrderByAttemptNumberDesc(paymentId)
                .orElseThrow(() -> new PaymentException(HttpStatus.CONFLICT, "Payment attempt history is missing"));
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> search(PaymentStatus status, Long settlementId, String claimId,
                                        String memberId, RecipientType recipientType,
                                        Instant from, Instant to, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, "from must be earlier than or equal to to");
        }
        Specification<Payment> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("paymentStatus"), status));
        if (settlementId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("settlementId"), settlementId));
        if (claimId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("claimId"), claimId));
        if (memberId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("memberId"), memberId));
        if (recipientType != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("recipientType"), recipientType));
        if (from != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), from));
        if (to != null) spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("createdAt"), to));
        return payments.findAll(spec, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public java.util.List<Long> dueCallbackIds(int batchSize) {
        return callbacks.findDueIds(CallbackStatus.PENDING, Instant.now(), Pageable.ofSize(batchSize));
    }

    @Transactional(readOnly = true)
    public java.util.List<Long> gatewayContinuationIds(int batchSize) {
        return callbacks.findGatewayContinuationIds(CallbackStatus.DELIVERED, Pageable.ofSize(batchSize));
    }

    @Transactional(readOnly = true)
    public java.util.List<Long> staleCallbackIds(Instant cutoff, int batchSize) {
        return callbacks.findStaleDeliveryIds(CallbackStatus.DELIVERING, cutoff, Pageable.ofSize(batchSize));
    }

    @Transactional(readOnly = true)
    public Page<Long> processingPaymentIds(Pageable pageable) {
        return payments.findByPaymentStatus(PaymentStatus.PROCESSING, pageable).map(Payment::getId);
    }

    private Payment lock(Long id) {
        return payments.findByIdForUpdate(id)
                .orElseThrow(() -> new PaymentException(HttpStatus.NOT_FOUND, "Payment was not found"));
    }

    private void audit(Payment payment, PaymentStatus previous, PaymentStatus next,
                       String action, String remarks) {
        PaymentAudit audit = new PaymentAudit();
        audit.setPayment(payment);
        audit.setPreviousStatus(previous);
        audit.setNewStatus(next);
        audit.setAction(action);
        audit.setRemarks(remarks);
        payment.getAuditHistory().add(audit);
        audits.save(audit);
    }

    private CallbackDelivery newCallback(Payment payment, CallbackTargetStatus target,
                                         String remarks, boolean startGateway) {
        CallbackDelivery delivery = new CallbackDelivery();
        delivery.setPayment(payment);
        delivery.setTargetStatus(target);
        delivery.setDeliveryStatus(CallbackStatus.PENDING);
        delivery.setRemarks(remarks);
        delivery.setAttemptCount(0);
        delivery.setMaxAttempts(callbackMaxAttempts);
        delivery.setNextAttemptAt(Instant.now().plusMillis(callbackInitialDelayMs));
        delivery.setStartGatewayAfterDelivery(startGateway);
        return callbacks.save(delivery);
    }

    public record CreatedPayment(PaymentResponse response, Long callbackId) {
    }

    public record AppliedGatewayResult(PaymentResponse response, Long callbackId) {
    }

    public record GatewayAttempt(Long paymentId, PaymentTransaction transaction) {
    }

    public record ExistingPayment(String requestHash, PaymentResponse response) {
    }
}
