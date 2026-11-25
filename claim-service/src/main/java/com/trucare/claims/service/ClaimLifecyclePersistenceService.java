package com.trucare.claims.service;

import com.trucare.claims.exception.ClaimNotFoundException;
import com.trucare.claims.exception.InvalidClaimTransitionException;
import com.trucare.claims.model.Claim;
import com.trucare.claims.model.ClaimStatus;
import com.trucare.claims.repository.ClaimRepository;
import com.trucare.claims.dto.ChangeClaimStatusRequest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class ClaimLifecyclePersistenceService {
    private final ClaimRepository claimRepository;
    private final ClaimLifecycleValidator lifecycleValidator;

    public ClaimLifecyclePersistenceService(ClaimRepository claimRepository,
                                            ClaimLifecycleValidator lifecycleValidator) {
        this.claimRepository = claimRepository;
        this.lifecycleValidator = lifecycleValidator;
    }

    @Transactional
    public Claim transition(String claimId, Long expectedVersion,
                            ChangeClaimStatusRequest request, boolean documentsValidated) {
        Claim claim = claimRepository.findByClaimId(claimId)
                .orElseThrow(() -> new ClaimNotFoundException("claim not found with id " + claimId));
        if (!Objects.equals(expectedVersion, claim.getVersion())) {
            throw new InvalidClaimTransitionException(
                    "Claim status changed concurrently; reload and retry the requested transition");
        }

        ClaimStatus current = claim.getClaimStatus();
        ClaimStatus requested = request.nextStatus();
        lifecycleValidator.validate(current, requested);
        if (current == ClaimStatus.PENDING_DOCUMENTS && requested == ClaimStatus.SUBMITTED
                && !documentsValidated) {
            throw new InvalidClaimTransitionException(
                    "Claim status transition from PENDING_DOCUMENTS to SUBMITTED requires validated documents");
        }
        if (requested == ClaimStatus.REJECTED && (request.reason() == null || request.reason().isBlank())) {
            throw new InvalidClaimTransitionException(
                    "Claim status transition from UNDER_REVIEW to REJECTED requires a rejection reason");
        }
        if (requested == ClaimStatus.SETTLEMENT_PENDING) {
            throw new InvalidClaimTransitionException(
                    "Claim status transition from APPROVED to SETTLEMENT_PENDING must be initiated by Settlement Service");
        }
        if (requested == ClaimStatus.SETTLED) {
            throw new InvalidClaimTransitionException(
                    "Claim status transition from SETTLEMENT_PENDING to SETTLED requires confirmed payment completion");
        }

        claim.setClaimStatus(requested);
        claim.setRejectionReason(requested == ClaimStatus.REJECTED ? request.reason().trim() : null);
        claim.setUpdatedAt(LocalDateTime.now());
        try {
            return claimRepository.saveAndFlush(claim);
        } catch (OptimisticLockingFailureException exception) {
            throw new InvalidClaimTransitionException(
                    "Claim status changed concurrently from " + current + " to another status; reload and retry");
        }
    }
}
