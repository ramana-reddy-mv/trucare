package com.trucare.payment.controller;

import com.trucare.payment.dto.*;
import com.trucare.payment.model.PaymentStatus;
import com.trucare.payment.model.RecipientType;
import com.trucare.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> initiate(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InitiatePaymentRequest request) {
        PaymentInitiationResult result = paymentService.initiate(request, idempotencyKey);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result.payment());
    }

    @PostMapping("/{paymentId}/retry")
    public PaymentResponse retry(@PathVariable Long paymentId) {
        return paymentService.retry(paymentId);
    }

    @PostMapping("/{paymentId}/reconcile")
    public PaymentResponse reconcile(@PathVariable Long paymentId) {
        return paymentService.reconcile(paymentId);
    }

    @PostMapping("/{paymentId}/callbacks/retry")
    public PaymentResponse retryCallbacks(@PathVariable Long paymentId) {
        return paymentService.retryCallbacks(paymentId);
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse get(@PathVariable Long paymentId) {
        return paymentService.get(paymentId);
    }

    @GetMapping("/reference/{paymentReference}")
    public PaymentResponse getByReference(@PathVariable String paymentReference) {
        return paymentService.getByReference(paymentReference);
    }

    @GetMapping("/settlement/{settlementId}")
    public PaymentResponse getBySettlement(@PathVariable Long settlementId) {
        return paymentService.getBySettlement(settlementId);
    }

    @GetMapping
    public Page<PaymentResponse> search(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) Long settlementId,
            @RequestParam(required = false) String claimId,
            @RequestParam(required = false) String memberId,
            @RequestParam(required = false) RecipientType recipientType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return paymentService.search(status, settlementId, claimId, memberId, recipientType,
                from, to, pageable);
    }
}
