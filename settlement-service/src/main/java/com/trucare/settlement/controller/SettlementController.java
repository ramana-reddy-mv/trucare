package com.trucare.settlement.controller;

import com.trucare.settlement.dto.CancelSettlementRequest;
import com.trucare.settlement.dto.CreateSettlementRequest;
import com.trucare.settlement.dto.PaymentStatusRequest;
import com.trucare.settlement.dto.SettlementResponse;
import com.trucare.settlement.model.SettlementStatus;
import com.trucare.settlement.model.SettlementType;
import com.trucare.settlement.service.SettlementService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.Instant;

@RestController
@RequestMapping("/api/settlements")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementResponse create(@Valid @RequestBody CreateSettlementRequest request,
                                     @RequestHeader("Idempotency-Key") String idempotencyKey,
                                     Authentication authentication) {
        return settlementService.create(request, idempotencyKey, authentication.getName());
    }

    @GetMapping("/{settlementId}")
    public SettlementResponse getById(@PathVariable Long settlementId) {
        return settlementService.getById(settlementId);
    }

    @GetMapping("/number/{settlementNumber}")
    public SettlementResponse getByNumber(@PathVariable String settlementNumber) {
        return settlementService.getByNumber(settlementNumber);
    }

    @GetMapping("/claim/{claimId}")
    public SettlementResponse getByClaimId(@PathVariable String claimId) {
        return settlementService.getByClaimId(claimId);
    }

    @GetMapping
    public Page<SettlementResponse> search(
            @RequestParam(required = false) SettlementStatus status,
            @RequestParam(required = false) SettlementType type,
            @RequestParam(required = false) String memberId,
            @RequestParam(required = false) String providerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        if (pageable.getPageSize() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size cannot exceed 100");
        }
        return settlementService.search(status, type, memberId, providerId, from, to, pageable);
    }

    @PostMapping("/{settlementId}/calculate")
    public SettlementResponse calculate(@PathVariable Long settlementId, Authentication authentication) {
        return settlementService.calculate(settlementId, authentication.getName());
    }

    @PostMapping("/{settlementId}/ready-for-payment")
    public SettlementResponse readyForPayment(@PathVariable Long settlementId, Authentication authentication) {
        return settlementService.readyForPayment(settlementId, authentication.getName());
    }

    @PostMapping("/{settlementId}/retry")
    public SettlementResponse retry(@PathVariable Long settlementId, Authentication authentication) {
        return settlementService.retry(settlementId, authentication.getName());
    }

    @PostMapping("/{settlementId}/payment-status")
    public SettlementResponse updatePaymentStatus(@PathVariable Long settlementId,
                                                  @Valid @RequestBody PaymentStatusRequest request,
                                                  Authentication authentication) {
        return settlementService.updatePaymentStatus(settlementId, request, authentication.getName());
    }

    @PostMapping("/{settlementId}/cancel")
    public SettlementResponse cancel(@PathVariable Long settlementId,
                                     @Valid @RequestBody CancelSettlementRequest request,
                                     Authentication authentication) {
        return settlementService.cancel(settlementId, request, authentication.getName());
    }
}
