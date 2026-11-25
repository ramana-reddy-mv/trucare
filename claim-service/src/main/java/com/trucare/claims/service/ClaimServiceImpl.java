package com.trucare.claims.service;


import com.trucare.claims.exception.ClaimNotFoundException;
import com.trucare.claims.exception.PolicyEligibilityException;
import com.trucare.claims.exception.PolicyNotFoundException;
import com.trucare.claims.exception.MemberNotFoundException;
import com.trucare.claims.exception.ProviderNotFoundException;
import com.trucare.claims.mapper.ClaimMapper;
import com.trucare.claims.model.Claim;
import com.trucare.claims.model.ClaimStatus;
import com.trucare.claims.dto.ChangeClaimStatusRequest;
import com.trucare.claims.repository.ClaimRepository;
import com.trucare.shared.claim.ClaimDTO;
import com.trucare.shared.claim.CreateClaimDTO;
import com.trucare.shared.claim.UpdateClaimDTO;
import com.trucare.shared.document.DocumentDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import org.springframework.data.domain.Pageable;
import com.trucare.shared.claim.ClaimPageDTO;
import com.trucare.shared.claim.ClaimDashboardSummaryDTO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;
import java.util.stream.Collectors;


@Service("claimSync")
public class ClaimServiceImpl implements ClaimService {

    private final ClaimRepository claimRepository;
    private final ClaimPersistenceService claimPersistenceService;
    private final ClaimMapper claimMapper;
    private final ClaimValidationService claimValidationService;
    private final ClaimPolicyEligibilityService claimPolicyEligibilityService;
    private final ObjectMapper objectMapper;
    private final ClaimLifecycleValidator claimLifecycleValidator;
    private final ClaimDocumentValidationService claimDocumentValidationService;
    private final ClaimLifecyclePersistenceService claimLifecyclePersistenceService;

    @Autowired
    public ClaimServiceImpl(ClaimRepository claimRepository, ClaimPersistenceService claimPersistenceService,
                            ClaimMapper claimMapper,
                            ClaimValidationService claimValidationService,
                            ClaimPolicyEligibilityService claimPolicyEligibilityService,
                            ObjectMapper objectMapper,
                            ClaimLifecycleValidator claimLifecycleValidator,
                            ClaimDocumentValidationService claimDocumentValidationService,
                            ClaimLifecyclePersistenceService claimLifecyclePersistenceService) {
        this.claimRepository = claimRepository;
        this.claimPersistenceService = claimPersistenceService;
        this.claimMapper = claimMapper;
        this.claimValidationService = claimValidationService;
        this.claimPolicyEligibilityService = claimPolicyEligibilityService;
        this.objectMapper = objectMapper;
        this.claimLifecycleValidator = claimLifecycleValidator;
        this.claimDocumentValidationService = claimDocumentValidationService;
        this.claimLifecyclePersistenceService = claimLifecyclePersistenceService;
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
            if ("Policy not found".equalsIgnoreCase(eligibility.reason())) {
                throw new PolicyNotFoundException(
                        "Policy " + createClaimDTO.getPolicyNumber() + " was not found");
            }
            throw new PolicyEligibilityException(
                    eligibility.reason() == null || eligibility.reason().isBlank()
                            ? "Policy is not eligible for this treatment"
                            : eligibility.reason());
        }

        // Validate Member and Provider using Feign Clients with circuit breakers
        if (!claimValidationService.validateMember(createClaimDTO.getMemberId())) {
            throw new MemberNotFoundException(
                    "Member " + createClaimDTO.getMemberId() + " was not found or is invalid");
        }
        if (!claimValidationService.validateProvider(createClaimDTO.getProviderId())) {
            throw new ProviderNotFoundException(
                    "Provider " + createClaimDTO.getProviderId() + " was not found or is invalid");
        }

        String claimId = "CLM"+ UUID.randomUUID().toString().substring(0,6).toUpperCase();

        // Save Claim with Pending Status
        Claim claim = new Claim();
        claim.setClaimId(claimId);
        claim.setMemberId(createClaimDTO.getMemberId());
        claim.setProviderId(createClaimDTO.getProviderId());
        claim.setPolicyNumber(createClaimDTO.getPolicyNumber());
        claim.setTreatmentType(createClaimDTO.getTreatmentType());
        claim.setTreatmentDate(createClaimDTO.getTreatmentDate());
        claim.setClaimAmount(createClaimDTO.getClaimAmount());
        claim.setClaimStatus(ClaimStatus.PENDING_DOCUMENTS);
        LocalDateTime createdAt = LocalDateTime.now();
        claim.setCreatedAt(createdAt);
        claim.setUpdatedAt(createdAt);
        claim.setDocumentIds(new ArrayList<>());
        claim.setIdempotencyKey(normalizedKey);
        claim.setRequestHash(requestHash);

        try {
            Claim savedClaim = claimPersistenceService.create(claim);
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
    public ClaimPageDTO<ClaimDTO> searchClaims(String status, String memberId, String providerId,
                                               LocalDate from, LocalDate to, String claimId, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        }
        ClaimStatus parsedStatus = null;
        if (status != null && !status.isBlank()) {
            try { parsedStatus = ClaimStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid claim status");
            }
        }
        var result = claimRepository.search(parsedStatus, blankToNull(memberId), blankToNull(providerId),
                from == null ? null : from.atStartOfDay(), to == null ? null : to.plusDays(1).atStartOfDay(),
                blankToNull(claimId), pageable);
        return new ClaimPageDTO<>(result.getContent().stream().map(claimMapper::toClaimDTO).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
                result.isFirst(), result.isLast());
    }

    @Override
    public ClaimDashboardSummaryDTO dashboardSummary() {
        Map<String, Long> counts = claimRepository.countByStatus().stream().collect(Collectors.toMap(
                row -> ((ClaimStatus) row[0]).name(), row -> (Long) row[1]));
        return new ClaimDashboardSummaryDTO(claimRepository.count(),
                counts.entrySet().stream().filter(e -> e.getKey().startsWith("PENDING") || e.getKey().equals("SUBMITTED") || e.getKey().equals("UNDER_REVIEW")).mapToLong(Map.Entry::getValue).sum(),
                counts.getOrDefault("APPROVED", 0L), counts.getOrDefault("REJECTED", 0L),
                claimRepository.countByCreatedAtGreaterThanEqual(YearMonth.now().atDay(1).atStartOfDay()),
                counts.entrySet().stream().map(e -> new ClaimDashboardSummaryDTO.StatusCount(e.getKey(), e.getValue())).toList());
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    @Override
    public ClaimDTO updateClaim(String claimId, UpdateClaimDTO updateClaimDTO) {
        Claim claim = claimRepository.findByClaimId(claimId)
                .orElseThrow(() -> new ClaimNotFoundException("claim not found with id " + claimId));

        if (updateClaimDTO.getClaimStatuses() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Claim status must be changed through PUT /api/claims/{claimId}/status");
        }

        if(updateClaimDTO.getClaimAmount()!=null){
            claim.setClaimAmount(updateClaimDTO.getClaimAmount());
        }
        if(updateClaimDTO.getClaimId()!=null){
            claim.setClaimId(String.valueOf(updateClaimDTO.getClaimId()));
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
        claim.setUpdatedAt(LocalDateTime.now());

        claimRepository.save(claim);
        return claimMapper.toClaimDTO(claim);
    }

    @Override
    public ClaimDTO transitionClaimStatus(String claimId, ChangeClaimStatusRequest request) {
        Claim claim = claimRepository.findByClaimId(claimId)
                .orElseThrow(() -> new ClaimNotFoundException("claim not found with id " + claimId));
        ClaimStatus currentStatus = claim.getClaimStatus();
        ClaimStatus requestedStatus = request.nextStatus();
        claimLifecycleValidator.validate(currentStatus, requestedStatus);
        boolean documentsValidated = false;
        if (currentStatus == ClaimStatus.PENDING_DOCUMENTS && requestedStatus == ClaimStatus.SUBMITTED) {
            claimDocumentValidationService.requireUploadedDocuments(claim.getDocumentIds());
            documentsValidated = true;
        }
        Claim updated = claimLifecyclePersistenceService.transition(
                claimId, claim.getVersion(), request, documentsValidated);
        return claimMapper.toClaimDTO(updated);
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
