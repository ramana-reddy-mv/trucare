package com.trucare.claims.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trucare.claims.client.PolicyEligibilityResponse;
import com.trucare.claims.mapper.ClaimMapper;
import com.trucare.claims.model.Claim;
import com.trucare.claims.repository.ClaimRepository;
import com.trucare.shared.claim.ClaimDTO;
import com.trucare.shared.claim.CreateClaimDTO;
import com.trucare.claims.dto.ChangeClaimStatusRequest;
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
    private ClaimLifecycleValidator claimLifecycleValidator;
    private ClaimDocumentValidationService claimDocumentValidationService;
    private ClaimLifecyclePersistenceService claimLifecyclePersistenceService;
    private ClaimServiceImpl claimService;

    @BeforeEach
    void setUp() {
        claimRepository = mock(ClaimRepository.class);
        claimPersistenceService = mock(ClaimPersistenceService.class);
        claimMapper = mock(ClaimMapper.class);
        claimValidationService = mock(ClaimValidationService.class);
        claimPolicyEligibilityService = mock(ClaimPolicyEligibilityService.class);
        claimLifecycleValidator = new ClaimLifecycleValidator();
        claimDocumentValidationService = mock(ClaimDocumentValidationService.class);
        claimLifecyclePersistenceService = new ClaimLifecyclePersistenceService(
                claimRepository, claimLifecycleValidator);
        claimService = new ClaimServiceImpl(
                claimRepository, claimPersistenceService, claimMapper, claimValidationService,
                claimPolicyEligibilityService, new ObjectMapper().findAndRegisterModules(),
                claimLifecycleValidator, claimDocumentValidationService, claimLifecyclePersistenceService);
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
    void statusTransitionReadsPersistedStatusAndPersistsOnlyAfterValidation() {
        Claim claim = claimWithStatus(ClaimStatus.SUBMITTED);
        when(claimRepository.findByClaimId("CLM-1")).thenReturn(Optional.of(claim));
        when(claimRepository.saveAndFlush(claim)).thenReturn(claim);
        ClaimDTO response = new ClaimDTO();
        when(claimMapper.toClaimDTO(claim)).thenReturn(response);

        ClaimDTO updated = claimService.transitionClaimStatus(
                "CLM-1", new ChangeClaimStatusRequest(ClaimStatus.UNDER_REVIEW, null));

        assertEquals(response, updated);
        assertEquals(ClaimStatus.UNDER_REVIEW, claim.getClaimStatus());
        assertNotNull(claim.getUpdatedAt());
        verify(claimRepository).saveAndFlush(claim);
    }

    @Test
    void invalidTransitionDoesNotModifyOrPersistClaim() {
        Claim claim = claimWithStatus(ClaimStatus.PENDING_DOCUMENTS);
        when(claimRepository.findByClaimId("CLM-1")).thenReturn(Optional.of(claim));

        assertThrows(com.trucare.claims.exception.InvalidClaimTransitionException.class,
                () -> claimService.transitionClaimStatus(
                        "CLM-1", new ChangeClaimStatusRequest(ClaimStatus.APPROVED, null)));

        assertEquals(ClaimStatus.PENDING_DOCUMENTS, claim.getClaimStatus());
        verify(claimRepository, never()).saveAndFlush(any());
    }

    @Test
    void missingClaimThrowsNotFoundBeforeTransitionValidation() {
        when(claimRepository.findByClaimId("missing")).thenReturn(Optional.empty());
        assertThrows(com.trucare.claims.exception.ClaimNotFoundException.class,
                () -> claimService.transitionClaimStatus(
                        "missing", new ChangeClaimStatusRequest(ClaimStatus.UNDER_REVIEW, null)));
        verify(claimRepository, never()).saveAndFlush(any());
    }

    @Test
    void submissionRequiresExistingValidatedDocuments() {
        Claim claim = claimWithStatus(ClaimStatus.PENDING_DOCUMENTS);
        claim.setDocumentIds(List.of(12L));
        when(claimRepository.findByClaimId("CLM-1")).thenReturn(Optional.of(claim));
        doThrow(new com.trucare.claims.exception.InvalidClaimTransitionException("missing documents"))
                .when(claimDocumentValidationService).requireUploadedDocuments(List.of(12L));

        assertThrows(com.trucare.claims.exception.InvalidClaimTransitionException.class,
                () -> claimService.transitionClaimStatus(
                        "CLM-1", new ChangeClaimStatusRequest(ClaimStatus.SUBMITTED, null)));
        verify(claimRepository, never()).saveAndFlush(any());
    }

    @Test
    void validDocumentsAllowSubmissionTransition() {
        Claim claim = claimWithStatus(ClaimStatus.PENDING_DOCUMENTS);
        claim.setDocumentIds(List.of(12L));
        when(claimRepository.findByClaimId("CLM-1")).thenReturn(Optional.of(claim));
        when(claimRepository.saveAndFlush(claim)).thenReturn(claim);
        doNothing().when(claimDocumentValidationService).requireUploadedDocuments(List.of(12L));

        claimService.transitionClaimStatus("CLM-1",
                new ChangeClaimStatusRequest(ClaimStatus.SUBMITTED, null));

        assertEquals(ClaimStatus.SUBMITTED, claim.getClaimStatus());
        verify(claimDocumentValidationService).requireUploadedDocuments(List.of(12L));
        verify(claimRepository).saveAndFlush(claim);
    }

    @Test
    void rejectionRequiresReasonAndRecordsIt() {
        Claim claim = claimWithStatus(ClaimStatus.UNDER_REVIEW);
        when(claimRepository.findByClaimId("CLM-1")).thenReturn(Optional.of(claim));
        when(claimRepository.saveAndFlush(claim)).thenReturn(claim);

        assertThrows(com.trucare.claims.exception.InvalidClaimTransitionException.class,
                () -> claimService.transitionClaimStatus(
                        "CLM-1", new ChangeClaimStatusRequest(ClaimStatus.REJECTED, " ")));
        verify(claimRepository, never()).saveAndFlush(any());

        claimService.transitionClaimStatus(
                "CLM-1", new ChangeClaimStatusRequest(ClaimStatus.REJECTED, "Not covered"));
        assertEquals("Not covered", claim.getRejectionReason());
        assertEquals(ClaimStatus.REJECTED, claim.getClaimStatus());
    }

    @Test
    void manualSettlementStatusRequestsAreRejectedUntilWorkflowConfirmsThem() {
        Claim approved = claimWithStatus(ClaimStatus.APPROVED);
        when(claimRepository.findByClaimId("CLM-1")).thenReturn(Optional.of(approved));
        assertThrows(com.trucare.claims.exception.InvalidClaimTransitionException.class,
                () -> claimService.transitionClaimStatus("CLM-1",
                        new ChangeClaimStatusRequest(ClaimStatus.SETTLEMENT_PENDING, null)));
        verify(claimRepository, never()).saveAndFlush(any());
    }

    @Test
    void optimisticLockFailureBecomesTransitionConflict() {
        Claim claim = claimWithStatus(ClaimStatus.SUBMITTED);
        when(claimRepository.findByClaimId("CLM-1")).thenReturn(Optional.of(claim));
        when(claimRepository.saveAndFlush(claim)).thenThrow(
                new org.springframework.orm.ObjectOptimisticLockingFailureException(Claim.class, "CLM-1"));

        assertThrows(com.trucare.claims.exception.InvalidClaimTransitionException.class,
                () -> claimService.transitionClaimStatus("CLM-1",
                        new ChangeClaimStatusRequest(ClaimStatus.UNDER_REVIEW, null)));
    }

    @Test
    void concurrentStatusChangeBetweenReadAndWriteReturnsConflict() {
        Claim observed = claimWithStatus(ClaimStatus.SUBMITTED);
        Claim concurrentlyUpdated = claimWithStatus(ClaimStatus.UNDER_REVIEW);
        concurrentlyUpdated.setVersion(1L);
        when(claimRepository.findByClaimId("CLM-1"))
                .thenReturn(Optional.of(observed), Optional.of(concurrentlyUpdated));

        assertThrows(com.trucare.claims.exception.InvalidClaimTransitionException.class,
                () -> claimService.transitionClaimStatus("CLM-1",
                        new ChangeClaimStatusRequest(ClaimStatus.UNDER_REVIEW, null)));
        verify(claimRepository, never()).saveAndFlush(any());
    }

    @Test
    void genericClaimUpdateCannotSetStatus() {
        Claim claim = claimWithStatus(ClaimStatus.PENDING_DOCUMENTS);
        when(claimRepository.findByClaimId("CLM-1")).thenReturn(Optional.of(claim));
        com.trucare.shared.claim.UpdateClaimDTO update = new com.trucare.shared.claim.UpdateClaimDTO();
        update.setClaimStatuses("APPROVED");
        assertThrows(ResponseStatusException.class, () -> claimService.updateClaim("CLM-1", update));
        assertEquals(ClaimStatus.PENDING_DOCUMENTS, claim.getClaimStatus());
        verify(claimRepository, never()).save(any());
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

    private Claim claimWithStatus(ClaimStatus status) {
        Claim claim = new Claim();
        claim.setClaimId("CLM-1");
        claim.setClaimStatus(status);
        claim.setVersion(0L);
        return claim;
    }

    private PolicyEligibilityResponse eligibleResponse() {
        return new PolicyEligibilityResponse(
                true, 1L, "POL1001", "member-1",
                new BigDecimal("500000.00"), new BigDecimal("500000.00"),
                "HOSPITALIZATION", new BigDecimal("300000.00"), "eligible");
    }
}
