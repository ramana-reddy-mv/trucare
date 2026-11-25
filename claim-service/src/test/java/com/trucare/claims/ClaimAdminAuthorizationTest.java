package com.trucare.claims;

import com.trucare.claims.config.ClaimSecurityConfig;
import com.trucare.claims.config.ClaimExceptionHandler;
import com.trucare.claims.controller.ClaimController;
import com.trucare.claims.service.ClaimCreationResult;
import com.trucare.claims.service.ClaimService;
import com.trucare.shared.claim.ClaimDTO;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;
import com.trucare.claims.dto.CreateClaimRequest;
import com.trucare.claims.dto.ChangeClaimStatusRequest;
import com.trucare.claims.exception.InvalidClaimTransitionException;
import com.trucare.claims.exception.ClaimNotFoundException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.hasItem;

@WebMvcTest(ClaimController.class)
@Import({ClaimSecurityConfig.class, ClaimExceptionHandler.class})
class ClaimAdminAuthorizationTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean(name = "claimSync") private ClaimService claimService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        TestJwtKeys.configure(registry);
    }

    @Test
    void adminRoleClaimCanPostToExactClaimsCollectionPath() throws Exception {
        when(claimService.createClaim(any(), eq("admin-post-test")))
                .thenReturn(new ClaimCreationResult(new ClaimDTO(), false));

        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TestJwtKeys.token("ADMIN"))
                        .header("Idempotency-Key", "admin-post-test")
                        .content("{\"memberId\":\"M-1\",\"providerId\":\"P-1\","
                                + "\"policyNumber\":\"POL-1\",\"treatmentType\":\"HOSPITALIZATION\","
                                + "\"treatmentDate\":\"2026-01-01\",\"claimAmount\":100.00}"))
                .andExpect(status().isCreated());

        verify(claimService).createClaim(any(), eq("admin-post-test"));
    }

    @Test
    void unrelatedRoleStillCannotPostClaim() throws Exception {
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TestJwtKeys.token("MEMBER"))
                        .header("Idempotency-Key", "member-post-test")
                        .content("{\"memberId\":\"M-1\",\"providerId\":\"P-1\","
                                + "\"policyNumber\":\"POL-1\",\"treatmentType\":\"HOSPITALIZATION\","
                                + "\"treatmentDate\":\"2026-01-01\",\"claimAmount\":100.00}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(claimService);
    }

    @Test
    void adminInvalidClaimReturnsValidationDetailsInsteadOfForbidden() throws Exception {
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TestJwtKeys.token("ADMIN"))
                        .header("Idempotency-Key", "invalid-claim-test")
                        .content("{\"policyNumber\":\"POL-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Claim request validation failed"))
                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("memberId")))
                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("providerId")))
                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("treatmentDate")));

        verifyNoInteractions(claimService);
    }

    @Test
    void invalidEnumAndMalformedJsonReturnBadRequestBeforeBusinessProcessing() throws Exception {
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TestJwtKeys.token("ADMIN"))
                        .header("Idempotency-Key", "invalid-enum-test")
                        .content("{\"memberId\":\"M-1\",\"providerId\":\"P-1\","
                                + "\"policyNumber\":\"POL-1\",\"treatmentType\":\"visit\","
                                + "\"treatmentDate\":\"2026-01-01\",\"claimAmount\":100.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TestJwtKeys.token("ADMIN"))
                        .header("Idempotency-Key", "malformed-json-test")
                        .content("{\"memberId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        verifyNoInteractions(claimService);
    }

    @Test
    void clientCannotSupplyServerManagedCreateFields() throws Exception {
        when(claimService.createClaim(any(), eq("managed-fields-test")))
                .thenReturn(new ClaimCreationResult(new ClaimDTO(), false));
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TestJwtKeys.token("ADMIN"))
                        .header("Idempotency-Key", "managed-fields-test")
                        .content("{\"memberId\":\"M-1\",\"providerId\":\"P-1\","
                                + "\"policyNumber\":\"POL-1\",\"treatmentType\":\"HOSPITALIZATION\","
                                + "\"treatmentDate\":\"2026-01-01\",\"claimAmount\":100.00,"
                                + "\"claimId\":\"ATTACKER-ID\",\"claimStatuses\":\"APPROVED\","
                                + "\"createdAt\":\"2000-01-01T00:00:00\","
                                + "\"updatedAt\":\"2000-01-01T00:00:00\",\"documentIds\":[99]}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateClaimRequest> request = ArgumentCaptor.forClass(CreateClaimRequest.class);
        verify(claimService).createClaim(request.capture(), eq("managed-fields-test"));
        assertEquals("M-1", request.getValue().getMemberId());
        assertEquals("HOSPITALIZATION", request.getValue().getTreatmentType());
        assertFalse(java.util.Arrays.stream(CreateClaimRequest.class.getSuperclass().getDeclaredFields())
                .anyMatch(field -> java.util.Set.of("claimId", "claimStatuses", "createdAt",
                        "updatedAt", "documentIds").contains(field.getName())));
    }

    @Test
    void adminIneligibleClaimPreserves422Reason() throws Exception {
        when(claimService.createClaim(any(), eq("ineligible-claim-test")))
                .thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Policy POL-1 is not eligible for this treatment"));

        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TestJwtKeys.token("ADMIN"))
                        .header("Idempotency-Key", "ineligible-claim-test")
                        .content("{\"memberId\":\"M-1\",\"providerId\":\"P-1\","
                                + "\"policyNumber\":\"POL-1\",\"treatmentType\":\"HOSPITALIZATION\","
                                + "\"treatmentDate\":\"2026-01-01\",\"claimAmount\":100.00}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Policy POL-1 is not eligible for this treatment"));
    }

    @Test
    void directExternalErrorPathRemainsDeniedForAdmin() throws Exception {
        mockMvc.perform(get("/error")
                        .header("Authorization", "Bearer " + TestJwtKeys.token("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalErrorDispatcherCanReachErrorController() throws Exception {
        mockMvc.perform(get("/error")
                .with(request -> {
                    request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);
                    return request;
                })
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/claims"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void claimOfficerCanUseStatusUpdateApiAndReceivesLifecycleResponse() throws Exception {
        when(claimService.transitionClaimStatus(eq("CLM-1"), any()))
                .thenReturn(new ClaimDTO());

        mockMvc.perform(put("/api/claims/CLM-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TestJwtKeys.token("CLAIM_OFFICER"))
                        .content("{\"nextStatus\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ChangeClaimStatusRequest> request =
                ArgumentCaptor.forClass(ChangeClaimStatusRequest.class);
        verify(claimService).transitionClaimStatus(eq("CLM-1"), request.capture());
        assertEquals(com.trucare.claims.model.ClaimStatus.UNDER_REVIEW, request.getValue().nextStatus());
    }

    @Test
    void lifecycleConflictIsReturnedAs409AndMemberCannotUseStatusApi() throws Exception {
        when(claimService.transitionClaimStatus(eq("CLM-1"), any()))
                .thenThrow(new InvalidClaimTransitionException(
                        "Claim status transition from PENDING_DOCUMENTS to APPROVED is not permitted"));
        mockMvc.perform(put("/api/claims/CLM-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TestJwtKeys.token("CLAIM_OFFICER"))
                        .content("{\"nextStatus\":\"APPROVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATE_TRANSITION"));

        mockMvc.perform(put("/api/claims/CLM-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TestJwtKeys.token("MEMBER"))
                        .content("{\"nextStatus\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingClaimOnStatusApiReturns404() throws Exception {
        when(claimService.transitionClaimStatus(eq("missing"), any()))
                .thenThrow(new ClaimNotFoundException("claim not found with id missing"));
        mockMvc.perform(put("/api/claims/missing/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TestJwtKeys.token("CLAIM_OFFICER"))
                        .content("{\"nextStatus\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}
