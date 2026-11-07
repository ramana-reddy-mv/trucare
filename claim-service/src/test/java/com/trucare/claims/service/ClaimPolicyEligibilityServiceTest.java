package com.trucare.claims.service;

import com.trucare.claims.client.PolicyClient;
import com.trucare.shared.claim.CreateClaimDTO;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ClaimPolicyEligibilityServiceTest {

    @Test
    void policyServiceFailureIsNotReportedAsBusinessIneligibility() {
        PolicyClient client = mock(PolicyClient.class);
        CreateClaimDTO claim = new CreateClaimDTO();
        ClaimPolicyEligibilityService service = new ClaimPolicyEligibilityService(client);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.policyServiceUnavailable(claim, new IllegalStateException("connection failed")));

        assertEquals(503, exception.getStatusCode().value());
    }
}
