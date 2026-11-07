package com.trucare.claims.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private ClaimServiceImpl claimService;

    @BeforeEach
    void setUp() {
        claimRepository = mock(ClaimRepository.class);
        claimMapper = mock(ClaimMapper.class);
        claimValidationService = mock(ClaimValidationService.class);
        claimService = new ClaimServiceImpl(
                claimRepository, claimMapper, claimValidationService, new ObjectMapper());
    }

    @Test
    void repeatedKeyReturnsExistingClaimWithoutCreatingAnother() {
        CreateClaimDTO request = validRequest();
        Claim persistedClaim = new Claim();
        ClaimDTO response = new ClaimDTO();

        when(claimRepository.findByIdempotencyKey("request-123"))
                .thenReturn(Optional.empty(), Optional.of(persistedClaim));
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

    private CreateClaimDTO validRequest() {
        CreateClaimDTO request = new CreateClaimDTO();
        request.setMemberId("member-1");
        request.setProviderId("provider-1");
        request.setClaimAmount(new BigDecimal("100.00"));
        return request;
    }
}
