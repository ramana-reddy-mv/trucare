package com.trucare.settlement.service;

import com.trucare.settlement.client.ClaimClient;
import com.trucare.settlement.dto.CancelSettlementRequest;
import com.trucare.settlement.dto.CreateSettlementRequest;
import com.trucare.settlement.dto.PaymentStatusRequest;
import com.trucare.settlement.dto.SettlementResponse;
import com.trucare.settlement.exception.ClaimServiceUnavailableException;
import com.trucare.settlement.exception.SettlementException;
import com.trucare.settlement.mapper.SettlementMapper;
import com.trucare.settlement.model.ComponentType;
import com.trucare.settlement.model.RecipientType;
import com.trucare.settlement.model.Settlement;
import com.trucare.settlement.model.SettlementAudit;
import com.trucare.settlement.model.SettlementBreakdown;
import com.trucare.settlement.model.SettlementPaymentTransaction;
import com.trucare.settlement.model.PaymentTransactionStatus;
import com.trucare.settlement.model.SettlementStatus;
import com.trucare.settlement.model.SettlementType;
import com.trucare.settlement.repository.SettlementAuditRepository;
import com.trucare.settlement.repository.SettlementPaymentTransactionRepository;
import com.trucare.settlement.repository.SettlementRepository;
import com.trucare.shared.claim.ClaimDTO;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final SettlementAuditRepository auditRepository;
    private final ClaimClient claimClient;
    private final SettlementMapper mapper;
    private final SettlementCalculationService calculationService;
    private final SettlementPaymentTransactionRepository paymentRepository;
    private final SettlementRequestFingerprint fingerprint;
    private final SettlementIdempotencyLookup idempotencyLookup;
    private final SettlementCreationTransaction creationTransaction;

    public SettlementService(SettlementRepository settlementRepository,
                             SettlementAuditRepository auditRepository,
                             ClaimClient claimClient,
                             SettlementMapper mapper,
                             SettlementCalculationService calculationService,
                             SettlementPaymentTransactionRepository paymentRepository,
                             SettlementRequestFingerprint fingerprint,
                             SettlementIdempotencyLookup idempotencyLookup,
                             SettlementCreationTransaction creationTransaction) {
        this.settlementRepository = settlementRepository;
        this.auditRepository = auditRepository;
        this.claimClient = claimClient;
        this.mapper = mapper;
        this.calculationService = calculationService;
        this.paymentRepository = paymentRepository;
        this.fingerprint = fingerprint;
        this.idempotencyLookup = idempotencyLookup;
        this.creationTransaction = creationTransaction;
    }

    public SettlementResponse create(CreateSettlementRequest request, String idempotencyKey, String actor) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isBlank() || key.length() > 128) {
            throw new SettlementException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must be non-blank and at most 128 characters");
        }
        String requestHash = fingerprint.calculate(request);
        SettlementResponse existing = existingResult(key, requestHash);
        if (existing != null) {
            return existing;
        }

        ClaimDTO claim = retrieveApprovedClaim(request);
        validateClaimIdentity(request, claim);
        if (claim.getMemberId() == null || claim.getMemberId().isBlank()) {
            throw new SettlementException(HttpStatus.BAD_GATEWAY,
                    "Claim Service returned an approved claim without a member ID");
        }
        BigDecimal approvedAmount = calculationService.normalize(claim.getClaimAmount(), "claimAmount");
        BigDecimal deductible = calculationService.normalize(request.getDeductibleAmount(), "deductibleAmount");
        BigDecimal copay = calculationService.normalize(request.getCopayAmount(), "copayAmount");
        BigDecimal nonPayable = calculationService.normalize(request.getNonPayableAmount(), "nonPayableAmount");
        calculationService.calculate(approvedAmount, deductible, copay, nonPayable);

        String memberId = claim.getMemberId().trim();
        String providerId = blankToNull(claim.getProviderId());
        SettlementType type = request.getSettlementType();
        if (type == SettlementType.CASHLESS && providerId == null) {
            throw new SettlementException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cashless settlement requires a provider on the approved claim");
        }

        Settlement settlement = new Settlement();
        settlement.setSettlementNumber("STL-" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
        settlement.setClaimId(claim.getClaimId());
        settlement.setIdempotencyKey(key);
        settlement.setRequestHash(requestHash);
        settlement.setMemberId(memberId);
        settlement.setProviderId(providerId);
        settlement.setPolicyId(request.getPolicyId());
        settlement.setSettlementType(type);
        settlement.setApprovedClaimAmount(approvedAmount);
        settlement.setDeductibleAmount(deductible);
        settlement.setCopayAmount(copay);
        settlement.setNonPayableAmount(nonPayable);
        settlement.setPayableAmount(BigDecimal.ZERO.setScale(2));
        settlement.setCurrency("INR");
        settlement.setSettlementStatus(SettlementStatus.PENDING);
        if (type == SettlementType.CASHLESS) {
            settlement.setRecipientType(RecipientType.PROVIDER);
            settlement.setRecipientId(providerId);
        } else {
            settlement.setRecipientType(RecipientType.MEMBER);
            settlement.setRecipientId(memberId);
        }

        try {
            SettlementResponse created = creationTransaction.persist(settlement, actor);
            log.info("Settlement created with status={}", SettlementStatus.PENDING);
            return created;
        } catch (DataIntegrityViolationException exception) {
            SettlementResponse concurrentResult = existingResult(key, requestHash);
            if (concurrentResult != null) {
                return concurrentResult;
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public SettlementResponse getById(Long id) {
        return mapper.toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public SettlementResponse getByNumber(String settlementNumber) {
        Settlement settlement = settlementRepository.findBySettlementNumber(settlementNumber)
                .orElseThrow(() -> new SettlementException(HttpStatus.NOT_FOUND, "Settlement was not found"));
        return mapper.toResponse(settlement);
    }

    @Transactional(readOnly = true)
    public SettlementResponse getByClaimId(String claimId) {
        Settlement settlement = settlementRepository.findByClaimId(claimId)
                .orElseThrow(() -> new SettlementException(HttpStatus.NOT_FOUND, "Settlement was not found"));
        return mapper.toResponse(settlement);
    }

    @Transactional(readOnly = true)
    public Page<SettlementResponse> search(SettlementStatus status, SettlementType type,
                                           String memberId, String providerId,
                                           Instant from, Instant to, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new SettlementException(HttpStatus.BAD_REQUEST, "from must be earlier than or equal to to");
        }
        Specification<Settlement> specification = (root, query, builder) -> builder.conjunction();
        if (status != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("settlementStatus"), status));
        }
        if (type != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("settlementType"), type));
        }
        if (memberId != null && !memberId.isBlank()) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("memberId"), memberId));
        }
        if (providerId != null && !providerId.isBlank()) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("providerId"), providerId));
        }
        if (from != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        return settlementRepository.findAll(specification, pageable).map(mapper::toSummaryResponse);
    }

    @Transactional
    public SettlementResponse calculate(Long id, String actor) {
        Settlement settlement = findForUpdate(id);
        requireStatus(settlement, SettlementStatus.PENDING);
        BigDecimal payable = calculationService.calculate(settlement.getApprovedClaimAmount(),
                settlement.getDeductibleAmount(), settlement.getCopayAmount(),
                settlement.getNonPayableAmount());

        settlement.getBreakdown().clear();
        addBreakdown(settlement, ComponentType.APPROVED_AMOUNT, "Approved claim amount",
                settlement.getApprovedClaimAmount());
        addBreakdown(settlement, ComponentType.DEDUCTIBLE, "Deductible",
                settlement.getDeductibleAmount());
        addBreakdown(settlement, ComponentType.COPAY, "Co-payment", settlement.getCopayAmount());
        addBreakdown(settlement, ComponentType.NON_PAYABLE, "Non-payable amount",
                settlement.getNonPayableAmount());
        addBreakdown(settlement, ComponentType.FINAL_PAYABLE, "Final payable amount", payable);
        settlement.setPayableAmount(payable);
        transition(settlement, SettlementStatus.CALCULATED, "CALCULATED",
                "Settlement amount calculated", actor);
        return mapper.toResponse(settlementRepository.save(settlement));
    }

    @Transactional
    public SettlementResponse readyForPayment(Long id, String actor) {
        Settlement settlement = findForUpdate(id);
        requireStatus(settlement, SettlementStatus.CALCULATED);
        transition(settlement, SettlementStatus.READY_FOR_PAYMENT, "READY_FOR_PAYMENT",
                "Settlement approved for payment processing", actor);
        return mapper.toResponse(settlementRepository.save(settlement));
    }

    @Transactional
    public SettlementResponse retry(Long id, String actor) {
        Settlement settlement = findForUpdate(id);
        requireStatus(settlement, SettlementStatus.FAILED);
        transition(settlement, SettlementStatus.READY_FOR_PAYMENT, "AUTHORIZED_RETRY",
                "Failed settlement authorized for retry", actor);
        settlement.setFailureReason(null);
        return mapper.toResponse(settlementRepository.save(settlement));
    }

    @Transactional
    public SettlementResponse updatePaymentStatus(Long id, PaymentStatusRequest request, String actor) {
        Settlement settlement = findForUpdate(id);
        SettlementStatus current = settlement.getSettlementStatus();
        SettlementStatus target = request.getStatus();

        if (target == SettlementStatus.FAILED
                && (request.getRemarks() == null || request.getRemarks().isBlank())) {
            throw new SettlementException(HttpStatus.BAD_REQUEST,
                    "Remarks are required when marking a settlement as failed");
        }
        if (current == SettlementStatus.READY_FOR_PAYMENT && target == SettlementStatus.PAYMENT_PROCESSING) {
            transition(settlement, target, "PAYMENT_PROCESSING", request.getRemarks(), actor);
        } else if (current == SettlementStatus.PAYMENT_PROCESSING
                && (target == SettlementStatus.SETTLED || target == SettlementStatus.FAILED)) {
            transition(settlement, target, "PAYMENT_STATUS_UPDATED", request.getRemarks(), actor);
            if (target == SettlementStatus.SETTLED) {
                settlement.setSettledAt(Instant.now());
                settlement.setFailureReason(null);
            } else {
                settlement.setFailureReason(request.getRemarks());
            }
        } else {
            throw new SettlementException(HttpStatus.CONFLICT,
                    "Invalid payment status transition from " + current + " to " + target);
        }
        recordPaymentTransaction(settlement, target, request);
        return mapper.toResponse(settlementRepository.save(settlement));
    }

    @Transactional
    public SettlementResponse cancel(Long id, CancelSettlementRequest request, String actor) {
        Settlement settlement = findForUpdate(id);
        if (settlement.getSettlementStatus() != SettlementStatus.PENDING
                && settlement.getSettlementStatus() != SettlementStatus.CALCULATED) {
            throw new SettlementException(HttpStatus.CONFLICT,
                    "Only pending or calculated settlements can be cancelled");
        }
        transition(settlement, SettlementStatus.CANCELLED, "CANCELLED", request.getReason(), actor);
        return mapper.toResponse(settlementRepository.save(settlement));
    }

    private ClaimDTO retrieveApprovedClaim(CreateSettlementRequest request) {
        ClaimDTO claim;
        try {
            claim = claimClient.getClaimByBusinessId(request.getClaimId().trim());
        } catch (FeignException.NotFound exception) {
            throw new SettlementException(HttpStatus.NOT_FOUND, "Claim was not found");
        } catch (FeignException exception) {
            throw new ClaimServiceUnavailableException("Claim Service request failed", exception);
        }
        if (claim == null || claim.getClaimId() == null || claim.getClaimAmount() == null
                || claim.getClaimStatuses() == null) {
            throw new SettlementException(HttpStatus.BAD_GATEWAY,
                    "Claim Service returned an incomplete claim response");
        }
        if (!"APPROVED".equalsIgnoreCase(claim.getClaimStatuses().trim())) {
            throw new SettlementException(HttpStatus.UNPROCESSABLE_ENTITY, "Claim is not approved");
        }
        return claim;
    }

    private void validateClaimIdentity(CreateSettlementRequest request, ClaimDTO claim) {
        if (!request.getClaimId().trim().equals(claim.getClaimId())) {
            throw new SettlementException(HttpStatus.BAD_GATEWAY,
                    "Claim Service returned a response for a different claim");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Settlement find(Long id) {
        return settlementRepository.findById(id)
                .orElseThrow(() -> new SettlementException(HttpStatus.NOT_FOUND, "Settlement was not found"));
    }

    private Settlement findForUpdate(Long id) {
        return settlementRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new SettlementException(HttpStatus.NOT_FOUND, "Settlement was not found"));
    }

    private void requireStatus(Settlement settlement, SettlementStatus expected) {
        if (settlement.getSettlementStatus() != expected) {
            throw new SettlementException(HttpStatus.CONFLICT,
                    "Operation requires settlement status " + expected);
        }
    }

    private void transition(Settlement settlement, SettlementStatus target,
                            String action, String remarks, String actor) {
        SettlementStatus previous = settlement.getSettlementStatus();
        settlement.setSettlementStatus(target);
        recordTransition(settlement, previous, target, action, remarks, actor);
    }

    private void recordTransition(Settlement settlement, SettlementStatus previous,
                                  SettlementStatus target, String action, String remarks, String actor) {
        SettlementAudit audit = new SettlementAudit();
        audit.setSettlement(settlement);
        audit.setPreviousStatus(previous);
        audit.setNewStatus(target);
        audit.setAction(action);
        audit.setRemarks(remarks);
        audit.setPerformedBy(actor);
        settlement.getAuditHistory().add(audit);
        auditRepository.save(audit);
    }

    private void addBreakdown(Settlement settlement, ComponentType componentType,
                              String description, BigDecimal amount) {
        SettlementBreakdown item = new SettlementBreakdown();
        item.setSettlement(settlement);
        item.setComponentType(componentType);
        item.setDescription(description);
        item.setAmount(amount);
        settlement.getBreakdown().add(item);
    }

    private SettlementResponse existingResult(String key, String requestHash) {
        return idempotencyLookup.find(key).map(existing -> {
            if (!existing.requestHash().equals(requestHash)) {
                throw new SettlementException(HttpStatus.CONFLICT,
                        "Idempotency-Key was already used with a different settlement request");
            }
            return existing.response();
        }).orElse(null);
    }

    private void recordPaymentTransaction(Settlement settlement, SettlementStatus target,
                                          PaymentStatusRequest request) {
        String reference = blankToNull(request.getPaymentReference());
        if (target == SettlementStatus.SETTLED && reference == null) {
            throw new SettlementException(HttpStatus.BAD_REQUEST,
                    "Payment reference is required when marking a settlement as settled");
        }

        SettlementPaymentTransaction transaction = null;
        if (reference != null) {
            transaction = paymentRepository.findByPaymentReference(reference).orElse(null);
            if (transaction != null && !transaction.getSettlement().getId().equals(settlement.getId())) {
                throw new SettlementException(HttpStatus.CONFLICT,
                        "Payment reference is already associated with another settlement");
            }
        }
        if (transaction == null) {
            transaction = new SettlementPaymentTransaction();
            transaction.setSettlement(settlement);
        }
        transaction.setPaymentReference(reference);
        transaction.setAmount(settlement.getPayableAmount());
        transaction.setCurrency(settlement.getCurrency());
        transaction.setDetails(request.getRemarks());
        transaction.setTransactionStatus(switch (target) {
            case PAYMENT_PROCESSING -> PaymentTransactionStatus.PROCESSING;
            case SETTLED -> PaymentTransactionStatus.SUCCEEDED;
            case FAILED -> PaymentTransactionStatus.FAILED;
            default -> throw new SettlementException(HttpStatus.CONFLICT,
                    "Unsupported payment transaction status");
        });
        if (!settlement.getPaymentTransactions().contains(transaction)) {
            settlement.getPaymentTransactions().add(transaction);
        }
        paymentRepository.save(transaction);
    }
}
