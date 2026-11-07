package com.trucare.payment.service;

import com.trucare.payment.client.SettlementClient;
import com.trucare.payment.client.SettlementResponse;
import com.trucare.payment.dto.*;
import com.trucare.payment.exception.PaymentException;
import com.trucare.payment.model.*;
import com.trucare.payment.repository.PaymentRepository;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
public class PaymentService {
    private final SettlementClient settlementClient;
    private final PaymentStore store;
    private final PaymentRepository paymentRepository;
    private final PaymentFingerprint fingerprint;
    private final PaymentGatewayProcessor gatewayProcessor;
    private final SettlementCallbackService callbackService;

    public PaymentService(SettlementClient settlementClient, PaymentStore store,
                          PaymentRepository paymentRepository, PaymentFingerprint fingerprint,
                          PaymentGatewayProcessor gatewayProcessor, SettlementCallbackService callbackService) {
        this.settlementClient = settlementClient;
        this.store = store;
        this.paymentRepository = paymentRepository;
        this.fingerprint = fingerprint;
        this.gatewayProcessor = gatewayProcessor;
        this.callbackService = callbackService;
    }

    public PaymentInitiationResult initiate(InitiatePaymentRequest request, String idempotencyKey) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isBlank() || key.length() > 128) {
            throw new PaymentException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must be non-blank and at most 128 characters");
        }
        String hash = fingerprint.calculate(request);
        PaymentStore.ExistingPayment existing = store.getExistingByKey(key);
        if (existing != null) {
            return replay(existing, hash);
        }

        SettlementResponse settlement;
        try {
            settlement = settlementClient.getSettlement(request.getSettlementId());
        } catch (FeignException.NotFound e) {
            throw new PaymentException(HttpStatus.NOT_FOUND, "Settlement was not found", e);
        } catch (FeignException e) {
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "Settlement Service is unavailable", e);
        }
        validateSettlement(request.getSettlementId(), settlement);

        Payment payment = new Payment();
        payment.setPaymentReference("PAY-" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
        payment.setSettlementId(settlement.id());
        payment.setSettlementNumber(settlement.settlementNumber());
        payment.setClaimId(settlement.claimId());
        payment.setMemberId(settlement.memberId());
        payment.setRecipientId(settlement.recipientId());
        payment.setRecipientType(RecipientType.valueOf(settlement.recipientType()));
        payment.setSettlementType(SettlementType.valueOf(settlement.settlementType()));
        payment.setAmount(settlement.payableAmount().setScale(2, java.math.RoundingMode.HALF_UP));
        payment.setCurrency(settlement.currency().toUpperCase(Locale.ROOT));
        payment.setPaymentStatus(PaymentStatus.INITIATED);
        payment.setPaymentMethod(request.getPaymentMethod().trim().toUpperCase(Locale.ROOT));
        payment.setIdempotencyKey(key);
        payment.setRequestHash(hash);

        PaymentStore.CreatedPayment created;
        try {
            created = store.create(payment);
        } catch (DataIntegrityViolationException e) {
            PaymentStore.ExistingPayment concurrent = store.getExistingByKey(key);
            if (concurrent != null) {
                return replay(concurrent, hash);
            }
            throw new PaymentException(HttpStatus.CONFLICT,
                    "A payment already exists for this settlement", e);
        }

        log.info("Payment created for settlementId={} with status={}",
                settlement.id(), PaymentStatus.INITIATED);
        callbackService.deliver(created.callbackId());
        return new PaymentInitiationResult(store.get(created.response().getId()), false);
    }

    public PaymentResponse retry(Long paymentId) {
        Payment payment = store.getEntity(paymentId);
        if (payment.getPaymentStatus() != PaymentStatus.FAILED) {
            throw new PaymentException(HttpStatus.CONFLICT, "Only failed payments can be retried");
        }
        SettlementResponse settlement;
        try {
            settlement = settlementClient.getSettlement(payment.getSettlementId());
        } catch (FeignException e) {
            throw new PaymentException(e.status() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.SERVICE_UNAVAILABLE,
                    "Settlement eligibility could not be verified", e);
        }
        if (!"READY_FOR_PAYMENT".equals(settlement.settlementStatus())) {
            throw new PaymentException(HttpStatus.CONFLICT,
                    "Settlement must be explicitly returned to READY_FOR_PAYMENT before payment retry");
        }
        Long callbackId = store.beginRetry(paymentId);
        callbackService.deliver(callbackId);
        return store.get(paymentId);
    }

    public PaymentResponse reconcile(Long paymentId) {
        return gatewayProcessor.reconcile(paymentId);
    }

    public PaymentResponse get(Long paymentId) {
        return store.get(paymentId);
    }

    public PaymentResponse getByReference(String reference) {
        return store.getByReference(reference);
    }

    public PaymentResponse getBySettlement(Long settlementId) {
        return store.getBySettlement(settlementId);
    }

    public Page<PaymentResponse> search(PaymentStatus status, Long settlementId, String claimId,
                                        String memberId, RecipientType recipientType,
                                        java.time.Instant from, java.time.Instant to, Pageable pageable) {
        if (pageable.getPageSize() > 100) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, "Page size cannot exceed 100");
        }
        return store.search(status, settlementId, claimId, memberId, recipientType, from, to, pageable);
    }

    public PaymentResponse retryCallbacks(Long paymentId) {
        store.get(paymentId);
        callbackService.retryDead(paymentId);
        return store.get(paymentId);
    }

    private PaymentInitiationResult replay(PaymentStore.ExistingPayment existing, String hash) {
        if (!existing.requestHash().equals(hash)) {
            throw new PaymentException(HttpStatus.CONFLICT,
                    "Idempotency-Key was already used with a different payment request");
        }
        return new PaymentInitiationResult(existing.response(), true);
    }

    private void validateSettlement(Long requestedId, SettlementResponse settlement) {
        if (settlement == null || settlement.id() == null || !requestedId.equals(settlement.id())
                || settlement.settlementNumber() == null || settlement.claimId() == null
                || settlement.memberId() == null || settlement.payableAmount() == null
                || settlement.currency() == null || settlement.settlementStatus() == null
                || settlement.recipientId() == null || settlement.recipientType() == null
                || settlement.settlementType() == null) {
            throw new PaymentException(HttpStatus.BAD_GATEWAY,
                    "Settlement Service returned an incomplete or mismatched settlement");
        }
        if (!"READY_FOR_PAYMENT".equals(settlement.settlementStatus())) {
            throw new PaymentException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Settlement is not ready for payment");
        }
        if (settlement.payableAmount().signum() <= 0) {
            throw new PaymentException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Settlement payable amount must be greater than zero");
        }
        if (!"INR".equalsIgnoreCase(settlement.currency())) {
            throw new PaymentException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Settlement currency is not supported");
        }
        try {
            SettlementType type = SettlementType.valueOf(settlement.settlementType());
            RecipientType recipient = RecipientType.valueOf(settlement.recipientType());
            RecipientType expected = type == SettlementType.CASHLESS
                    ? RecipientType.PROVIDER : RecipientType.MEMBER;
            if (recipient != expected) {
                throw new PaymentException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Settlement recipient type does not match settlement type");
            }
        } catch (IllegalArgumentException exception) {
            throw new PaymentException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Settlement type or recipient type is not supported");
        }
        if (settlement.recipientId().isBlank()) {
            throw new PaymentException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Settlement recipient ID is missing");
        }
    }
}
