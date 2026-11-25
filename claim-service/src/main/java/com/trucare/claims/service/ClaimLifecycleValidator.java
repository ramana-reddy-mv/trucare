package com.trucare.claims.service;

import com.trucare.claims.exception.InvalidClaimTransitionException;
import com.trucare.claims.model.ClaimStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Component
public class ClaimLifecycleValidator {
    private static final Map<ClaimStatus, Set<ClaimStatus>> PERMITTED_TRANSITIONS = transitions();
    private static final Set<ClaimStatus> TERMINAL_STATUSES =
            Set.of(ClaimStatus.REJECTED, ClaimStatus.SETTLED, ClaimStatus.CANCELLED);

    public void validate(ClaimStatus current, ClaimStatus requested) {
        if (current == null || requested == null) {
            throw new InvalidClaimTransitionException(
                    "Claim status transition requires both a current status and a requested status "
                            + "(current=" + current + ", requested=" + requested + ")");
        }
        if (TERMINAL_STATUSES.contains(current)) {
            throw new InvalidClaimTransitionException(
                    "Claim status transition from terminal state " + current + " to " + requested
                            + " is not permitted");
        }
        if (!PERMITTED_TRANSITIONS.getOrDefault(current, Set.of()).contains(requested)) {
            throw new InvalidClaimTransitionException(
                    "Claim status transition from " + current + " to " + requested + " is not permitted");
        }
    }

    static Map<ClaimStatus, Set<ClaimStatus>> permittedTransitions() {
        return PERMITTED_TRANSITIONS;
    }

    private static Map<ClaimStatus, Set<ClaimStatus>> transitions() {
        EnumMap<ClaimStatus, Set<ClaimStatus>> transitions = new EnumMap<>(ClaimStatus.class);
        transitions.put(ClaimStatus.PENDING_DOCUMENTS,
                Set.of(ClaimStatus.SUBMITTED, ClaimStatus.CANCELLED));
        transitions.put(ClaimStatus.SUBMITTED,
                Set.of(ClaimStatus.UNDER_REVIEW, ClaimStatus.CANCELLED));
        transitions.put(ClaimStatus.UNDER_REVIEW,
                Set.of(ClaimStatus.APPROVED, ClaimStatus.REJECTED));
        transitions.put(ClaimStatus.APPROVED, Set.of(ClaimStatus.SETTLEMENT_PENDING));
        transitions.put(ClaimStatus.SETTLEMENT_PENDING, Set.of(ClaimStatus.SETTLED));
        transitions.put(ClaimStatus.REJECTED, Set.of());
        transitions.put(ClaimStatus.SETTLED, Set.of());
        transitions.put(ClaimStatus.CANCELLED, Set.of());
        return Map.copyOf(transitions);
    }
}
