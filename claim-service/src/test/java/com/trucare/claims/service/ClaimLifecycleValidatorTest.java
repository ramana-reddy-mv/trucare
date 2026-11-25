package com.trucare.claims.service;

import com.trucare.claims.exception.InvalidClaimTransitionException;
import com.trucare.claims.model.ClaimStatus;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimLifecycleValidatorTest {
    private final ClaimLifecycleValidator validator = new ClaimLifecycleValidator();

    @Test
    void acceptsEveryExplicitlyPermittedTransition() {
        ClaimLifecycleValidator.permittedTransitions().forEach((current, requestedStatuses) ->
                requestedStatuses.forEach(requested ->
                        assertDoesNotThrow(() -> validator.validate(current, requested),
                                () -> current + " -> " + requested)));
    }

    @Test
    void rejectsEveryTransitionNotInTheExplicitMapIncludingSameStatus() {
        ClaimLifecycleValidator.permittedTransitions().forEach((current, permitted) ->
                Set.of(ClaimStatus.values()).stream()
                        .filter(requested -> !permitted.contains(requested))
                        .forEach(requested -> {
                            InvalidClaimTransitionException exception = assertThrows(
                                    InvalidClaimTransitionException.class,
                                    () -> validator.validate(current, requested));
                            assertTrue(exception.getMessage().contains(current.name()));
                            assertTrue(exception.getMessage().contains(requested.name()));
                        }));
    }

    @Test
    void rejectsTransitionsFromEveryTerminalStatus() {
        for (ClaimStatus terminal : Set.of(ClaimStatus.REJECTED, ClaimStatus.SETTLED, ClaimStatus.CANCELLED)) {
            for (ClaimStatus requested : ClaimStatus.values()) {
                InvalidClaimTransitionException exception = assertThrows(
                        InvalidClaimTransitionException.class,
                        () -> validator.validate(terminal, requested));
                assertTrue(exception.getMessage().contains(terminal.name()));
                assertTrue(exception.getMessage().contains(requested.name()));
            }
        }
    }

    @Test
    void rejectsNullCurrentOrRequestedStatus() {
        assertThrows(InvalidClaimTransitionException.class,
                () -> validator.validate(null, ClaimStatus.SUBMITTED));
        assertThrows(InvalidClaimTransitionException.class,
                () -> validator.validate(ClaimStatus.PENDING_DOCUMENTS, null));
        assertThrows(InvalidClaimTransitionException.class, () -> validator.validate(null, null));
    }
}
