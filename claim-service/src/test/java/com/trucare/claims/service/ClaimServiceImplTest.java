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
import java.util.ArrayList;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import com.trucare.claims.model.ClaimStatus;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClaimServiceImplTest {

    private ClaimRepository claimRepository;
    private ClaimPersistenceService claimPersistenceService;
    private ClaimMapper claimMapper;
    private ClaimValidationService claimValidationService;
    private ClaimPolicyEligibilityService claimPolicyEligibilityService;
    private ClaimServiceImpl claimService;

    @BeforeEach
    void setUp() {
        claimRepository = mock(ClaimRepository.class);
        claimPersistenceService = mock(ClaimPersistenceService.class);
        claimMapper = mock(ClaimMapper.class);
        claimValidationService = mock(ClaimValidationService.class);
        claimPolicyEligibilityService = mock(ClaimPolicyEligibilityService.class);
        claimService = new ClaimServiceImpl(
                claimRepository, claimPersistenceService, claimMapper, claimValidationService,
                claimPolicyEligibilityService, new ObjectMapper().findAndRegisterModules());
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
        when(claimPersistenceService.create(any(Claim.class))).thenAnswer(invocation -> {
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
        verify(claimPersistenceService, times(1)).create(any(Claim.class));
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
        when(claimPersistenceService.create(any(Claim.class))).thenAnswer(invocation -> {
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
        verify(claimPersistenceService, times(1)).create(any(Claim.class));
    }

    @Test
    void createsClaimUsingOnlyClientFieldsAndServerManagedLifecycleValues() {
        CreateClaimDTO request = validRequest();
        Claim persisted = new Claim();
        when(claimRepository.findByIdempotencyKey("request-server-fields")).thenReturn(Optional.empty());
        when(claimPolicyEligibilityService.checkEligibility(request)).thenReturn(eligibleResponse());
        when(claimValidationService.validateMember("member-1")).thenReturn(true);
        when(claimValidationService.validateProvider("provider-1")).thenReturn(true);
        when(claimPersistenceService.create(any(Claim.class))).thenAnswer(invocation -> {
            Claim candidate = invocation.getArgument(0);
            persisted.setId(1L);
            persisted.setClaimId(candidate.getClaimId());
            persisted.setMemberId(candidate.getMemberId());
            persisted.setProviderId(candidate.getProviderId());
            persisted.setPolicyNumber(candidate.getPolicyNumber());
            persisted.setTreatmentType(candidate.getTreatmentType());
            persisted.setTreatmentDate(candidate.getTreatmentDate());
            persisted.setClaimAmount(candidate.getClaimAmount());
            persisted.setClaimStatus(candidate.getClaimStatus());
            persisted.setCreatedAt(candidate.getCreatedAt());
            persisted.setUpdatedAt(candidate.getUpdatedAt());
            persisted.setDocumentIds(candidate.getDocumentIds());
            return persisted;
        });
        when(claimMapper.toClaimDTO(persisted)).thenReturn(new ClaimDTO());

        claimService.createClaim(request, "request-server-fields");

        assertTrue(persisted.getClaimId().matches("CLM[0-9A-F]{6}"));
        assertEquals(ClaimStatus.PENDING_DOCUMENTS, persisted.getClaimStatus());
        assertNotNull(persisted.getCreatedAt());
        assertEquals(persisted.getCreatedAt(), persisted.getUpdatedAt());
        assertEquals(new ArrayList<>(), persisted.getDocumentIds());
        assertEquals("member-1", persisted.getMemberId());
        assertEquals("provider-1", persisted.getProviderId());
        assertEquals("POL1001", persisted.getPolicyNumber());
        assertEquals("HOSPITALIZATION", persisted.getTreatmentType());
        assertEquals(request.getClaimAmount(), persisted.getClaimAmount());
    }

    @Test
    void memberFailureDoesNotPersistClaim() {
        CreateClaimDTO request = validRequest();
        when(claimRepository.findByIdempotencyKey("request-invalid-member")).thenReturn(Optional.empty());
        when(claimPolicyEligibilityService.checkEligibility(request)).thenReturn(eligibleResponse());
        when(claimValidationService.validateMember("member-1")).thenReturn(false);

        assertThrows(com.trucare.claims.exception.MemberNotFoundException.class,
                () -> claimService.createClaim(request, "request-invalid-member"));
        verify(claimPersistenceService, never()).create(any());
        verify(claimValidationService, never()).validateProvider(any());
    }

    @Test
    void providerFailureDoesNotPersistClaim() {
        CreateClaimDTO request = validRequest();
        when(claimRepository.findByIdempotencyKey("request-invalid-provider")).thenReturn(Optional.empty());
        when(claimPolicyEligibilityService.checkEligibility(request)).thenReturn(eligibleResponse());
        when(claimValidationService.validateMember("member-1")).thenReturn(true);
        when(claimValidationService.validateProvider("provider-1")).thenReturn(false);

        assertThrows(com.trucare.claims.exception.ProviderNotFoundException.class,
                () -> claimService.createClaim(request, "request-invalid-provider"));
        verify(claimPersistenceService, never()).create(any());
    }

    @Test
    void missingPolicyDoesNotPersistClaim() {
        CreateClaimDTO request = validRequest();
        when(claimRepository.findByIdempotencyKey("request-missing-policy")).thenReturn(Optional.empty());
        when(claimPolicyEligibilityService.checkEligibility(request)).thenReturn(
                new PolicyEligibilityResponse(false, null, "POL1001", "member-1", null,
                        null, "HOSPITALIZATION", BigDecimal.ZERO, "Policy not found"));

        assertThrows(com.trucare.claims.exception.PolicyNotFoundException.class,
                () -> claimService.createClaim(request, "request-missing-policy"));
        verify(claimPersistenceService, never()).create(any());
    }

    @Test
    void ineligiblePolicyDoesNotPersistClaim() {
        CreateClaimDTO request = validRequest();
        when(claimRepository.findByIdempotencyKey("request-ineligible")).thenReturn(Optional.empty());
        when(claimPolicyEligibilityService.checkEligibility(request)).thenReturn(
                new PolicyEligibilityResponse(false, 1L, "POL1001", "member-1",
                        null, null, "HOSPITALIZATION", null, "Applicable waiting period has not been completed"));

        assertThrows(com.trucare.claims.exception.PolicyEligibilityException.class,
                () -> claimService.createClaim(request, "request-ineligible"));
        verify(claimPersistenceService, never()).create(any());
        verifyNoInteractions(claimValidationService);
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
        request.setPolicyNumber("POL1001");
        request.setTreatmentType("HOSPITALIZATION");
        request.setTreatmentDate(LocalDate.of(2026, 1, 1));
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
