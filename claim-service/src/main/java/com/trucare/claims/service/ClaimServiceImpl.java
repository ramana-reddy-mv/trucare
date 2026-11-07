package com.trucare.claims.service;


import com.trucare.claims.exception.ClaimNotFoundException;
import com.trucare.claims.mapper.ClaimMapper;
import com.trucare.claims.model.Claim;
import com.trucare.claims.repository.ClaimRepository;
import com.trucare.shared.claim.ClaimDTO;
import com.trucare.shared.claim.CreateClaimDTO;
import com.trucare.shared.claim.UpdateClaimDTO;
import com.trucare.shared.document.DocumentDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service("claimSync")
public class ClaimServiceImpl implements ClaimService {

    private final ClaimRepository claimRepository;
    private final ClaimMapper claimMapper;
    private final ClaimValidationService claimValidationService;
    private final ClaimPolicyEligibilityService claimPolicyEligibilityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ClaimServiceImpl(ClaimRepository claimRepository, ClaimMapper claimMapper,
                            ClaimValidationService claimValidationService,
                            ClaimPolicyEligibilityService claimPolicyEligibilityService,
                            ObjectMapper objectMapper) {
        this.claimRepository = claimRepository;
        this.claimMapper = claimMapper;
        this.claimValidationService = claimValidationService;
        this.claimPolicyEligibilityService = claimPolicyEligibilityService;
        this.objectMapper = objectMapper;
    }

    // Create or update claim reactively
    @Override
    public ClaimCreationResult createClaim(CreateClaimDTO createClaimDTO, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must be non-blank and at most 128 characters");
        }

        String normalizedKey = idempotencyKey.trim();
        String requestHash = hashRequest(createClaimDTO);
        Optional<Claim> existingClaim = claimRepository.findByIdempotencyKey(normalizedKey);
        if (existingClaim.isPresent()) {
            return replayOrReject(existingClaim.get(), requestHash);
        }

        var eligibility = claimPolicyEligibilityService.checkEligibility(createClaimDTO);
        if (!eligibility.eligible()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    eligibility.reason() == null || eligibility.reason().isBlank()
                            ? "Policy is not eligible for this treatment"
                            : eligibility.reason());
        }

        // Validate Member and Provider using Feign Clients with circuit breakers
        boolean isMemberValid = claimValidationService.validateMember(createClaimDTO.getMemberId());
        boolean isProviderValid = claimValidationService.validateProvider(createClaimDTO.getProviderId());

        if (!isMemberValid || !isProviderValid) {
            throw new ValidationException("Invalid member or provider");
        }

        String claimId = "CLM"+ UUID.randomUUID().toString().substring(0,6).toUpperCase();

        // Save Claim with Pending Status
        Claim claim = claimMapper.toClaim(createClaimDTO);
        claim.setClaimId(claimId);
        claim.setClaimStatus("PENDING_DOCUMENTS");
        claim.setIdempotencyKey(normalizedKey);
        claim.setRequestHash(requestHash);

        try {
            Claim savedClaim = claimRepository.saveAndFlush(claim);
            return new ClaimCreationResult(claimMapper.toClaimDTO(savedClaim), false);
        } catch (DataIntegrityViolationException exception) {
            Optional<Claim> concurrentClaim = claimRepository.findByIdempotencyKey(normalizedKey);
            if (concurrentClaim.isPresent()) {
                return replayOrReject(concurrentClaim.get(), requestHash);
            }
            throw exception;
        }
    }

    private ClaimCreationResult replayOrReject(Claim existingClaim, String requestHash) {
        if (!requestHash.equals(existingClaim.getRequestHash())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency-Key was already used with a different claim request");
        }
        return new ClaimCreationResult(claimMapper.toClaimDTO(existingClaim), true);
    }

    private String hashRequest(CreateClaimDTO createClaimDTO) {
        try {
            byte[] requestJson = objectMapper.writeValueAsBytes(createClaimDTO);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(requestJson);
            return java.util.HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not fingerprint claim request", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @Override
    public ClaimDTO getClaimById(Long claimId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ClaimNotFoundException("claim not found with id "+claimId));
        return claimMapper.toClaimDTO(claim);
    }

    @Override
    public ClaimDTO getClaimByBusinessId(String claimId) {
        Claim claim = claimRepository.findByClaimId(claimId)
                .orElseThrow(() -> new ClaimNotFoundException("claim not found with id " + claimId));
        return claimMapper.toClaimDTO(claim);
    }

    @Override
    public List<ClaimDTO> getAllClaims() {
        List<Claim> claims = claimRepository.findAll();

        return claims.stream().map(claimMapper::toClaimDTO).toList();
    }

    @Override
    public ClaimDTO updateClaim(String claimId, UpdateClaimDTO updateClaimDTO) {
        Claim claim = claimRepository.findByClaimId(claimId)
                .orElseThrow(() -> new ClaimNotFoundException("claim not found with id"+ updateClaimDTO.getClaimId()));

        if(updateClaimDTO.getClaimAmount()!=null){
            claim.setClaimAmount(updateClaimDTO.getClaimAmount());
        }
        if(updateClaimDTO.getClaimId()!=null){
            claim.setClaimId(String.valueOf(updateClaimDTO.getClaimId()));
        }
        if(updateClaimDTO.getClaimStatuses()!=null){
            claim.setClaimStatus(updateClaimDTO.getClaimStatuses());
        }
        if(updateClaimDTO.getDocuments()!=null){
            claim.setDocumentIds(updateClaimDTO.getDocuments().stream().map(DocumentDTO::getId).toList());
        }
        if(updateClaimDTO.getMemberId()!=null){
            claim.setMemberId(String.valueOf(updateClaimDTO.getMemberId()));
        }
        if(updateClaimDTO.getProviderId()!=null){
            claim.setProviderId(String.valueOf(updateClaimDTO.getProviderId()));
        }
        if(updateClaimDTO.getUpdatedAt()!=null) {
            claim.setUpdatedAt(updateClaimDTO.getUpdatedAt());
        }

        claimRepository.save(claim);
        return claimMapper.toClaimDTO(claim);
    }

    @Override
    public String deleteClaim(Long claimId) {
        if(claimRepository.existsById(claimId)){
            claimRepository.deleteById(claimId);
        }else {
            return "claim not found with id" + claimId;
        }

        return "claim deleted successfully";
    }
}
