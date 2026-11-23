package com.trucare.claims.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trucare.claims.client.PolicyEligibilityResponse;
import com.trucare.claims.mapper.ClaimMapper;
import com.trucare.claims.model.Claim;
import com.trucare.claims.repository.ClaimRepository;
import com.trucare.shared.claim.ClaimDTO;
import com.trucare.shared.claim.CreateClaimDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import com.trucare.claims.model.ClaimStatus;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClaimServiceImplTest {

    private ClaimRepository claimRepository;
    private ClaimMapper claimMapper;
    private ClaimValidationService claimValidationService;
    private ClaimPolicyEligibilityService claimPolicyEligibilityService;
    private ClaimServiceImpl claimService;

    @BeforeEach
    void setUp() {
        claimRepository = mock(ClaimRepository.class);
        claimMapper = mock(ClaimMapper.class);
        claimValidationService = mock(ClaimValidationService.class);
        claimPolicyEligibilityService = mock(ClaimPolicyEligibilityService.class);
        claimService = new ClaimServiceImpl(
                claimRepository, claimMapper, claimValidationService,
                claimPolicyEligibilityService, new ObjectMapper());
    }

    @Test
    void repeatedKeyReturnsExistingClaimWithoutCreatingAnother() {
        CreateClaimDTO request = validRequest();
        Claim persistedClaim = new Claim();
        ClaimDTO response = new ClaimDTO();

        when(claimRepository.findByIdempotencyKey("request-123"))
                .thenReturn(Optional.empty(), Optional.of(persistedClaim));
        when(claimPolicyEligibilityService.checkEligibility(request)).thenReturn(eligibleResponse());
        when(claimValidationService.validateMember("member-1")).thenReturn(true);
        when(claimValidationService.validateProvider("provider-1")).thenReturn(true);
        when(claimMapper.toClaim(request)).thenReturn(new Claim());
        when(claimRepository.saveAndFlush(any(Claim.class))).thenAnswer(invocation -> {
            Claim claim = invocation.getArgument(0);
            persistedClaim.setIdempotencyKey(claim.getIdempotencyKey());
            persistedClaim.setRequestHash(claim.getRequestHash());
            return persistedClaim;
        });
        when(claimMapper.toClaimDTO(persistedClaim)).thenReturn(response);

        ClaimCreationResult first = claimService.createClaim(request, "request-123");
        ClaimCreationResult replay = claimService.createClaim(request, "request-123");

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(response, replay.claim());
        verify(claimRepository, times(1)).saveAndFlush(any(Claim.class));
        verify(claimValidationService, times(1)).validateMember("member-1");
    }

    @Test
    void reusedKeyWithDifferentRequestReturnsConflict() {
        CreateClaimDTO originalRequest = validRequest();
        Claim persistedClaim = new Claim();
        when(claimRepository.findByIdempotencyKey("request-123"))
                .thenReturn(Optional.empty(), Optional.of(persistedClaim));
        when(claimPolicyEligibilityService.checkEligibility(originalRequest)).thenReturn(eligibleResponse());
        when(claimValidationService.validateMember("member-1")).thenReturn(true);
        when(claimValidationService.validateProvider("provider-1")).thenReturn(true);
        when(claimMapper.toClaim(originalRequest)).thenReturn(new Claim());
        when(claimRepository.saveAndFlush(any(Claim.class))).thenAnswer(invocation -> {
            Claim claim = invocation.getArgument(0);
            persistedClaim.setIdempotencyKey(claim.getIdempotencyKey());
            persistedClaim.setRequestHash(claim.getRequestHash());
            return persistedClaim;
        });
        when(claimMapper.toClaimDTO(persistedClaim)).thenReturn(new ClaimDTO());

        claimService.createClaim(originalRequest, "request-123");
        CreateClaimDTO differentRequest = validRequest();
        differentRequest.setClaimAmount(new BigDecimal("200.00"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> claimService.createClaim(differentRequest, "request-123"));

        assertEquals(409, exception.getStatusCode().value());
        verify(claimRepository, times(1)).saveAndFlush(any(Claim.class));
    }

    @Test
    void searchesClaimsAsPageAndMapsRowsToDtos() {
        Claim entity = new Claim();
        ClaimDTO dto = new ClaimDTO();
        when(claimRepository.search(eq(ClaimStatus.APPROVED), eq("M1"), isNull(), any(), any(), eq("CLM"), any()))
                .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1));
        when(claimMapper.toClaimDTO(entity)).thenReturn(dto);

        var page = claimService.searchClaims("approved", "M1", null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "CLM", PageRequest.of(0, 10));

        assertEquals(List.of(dto), page.content());
        assertEquals(1, page.totalElements());
        verify(claimRepository).search(eq(ClaimStatus.APPROVED), eq("M1"), isNull(), any(), any(), eq("CLM"), any());
    }

    @Test
    void rejectsReversedSearchDateRange() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> claimService.searchClaims(null, null, null,
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null, PageRequest.of(0, 10)));
        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(claimRepository);
    }

    @Test
    void dashboardUsesGroupedDatabaseCounts() {
        when(claimRepository.countByStatus()).thenReturn(List.of(
                new Object[]{ClaimStatus.APPROVED, 2L}, new Object[]{ClaimStatus.REJECTED, 1L},
                new Object[]{ClaimStatus.PENDING_DOCUMENTS, 3L}));
        when(claimRepository.count()).thenReturn(6L);
        when(claimRepository.countByCreatedAtGreaterThanEqual(any())).thenReturn(4L);

        var summary = claimService.dashboardSummary();

        assertEquals(6, summary.totalClaims());
        assertEquals(3, summary.pendingClaims());
        assertEquals(2, summary.approvedClaims());
        assertEquals(1, summary.rejectedClaims());
        assertEquals(4, summary.claimsThisMonth());
        assertEquals(3, summary.statusDistribution().size());
    }

    private CreateClaimDTO validRequest() {
        CreateClaimDTO request = new CreateClaimDTO();
        request.setMemberId("member-1");
        request.setProviderId("provider-1");
        request.setClaimAmount(new BigDecimal("100.00"));
        return request;
    }

    private PolicyEligibilityResponse eligibleResponse() {
        return new PolicyEligibilityResponse(
                true, 1L, "POL1001", "member-1",
                new BigDecimal("500000.00"), new BigDecimal("500000.00"),
                "HOSPITALIZATION", new BigDecimal("300000.00"), "eligible");
    }
}
