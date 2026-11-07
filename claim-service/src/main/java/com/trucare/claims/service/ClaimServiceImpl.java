package com.trucare.claims.service;


import com.trucare.claims.exception.ClaimNotFoundException;
import com.trucare.claims.mapper.ClaimMapper;
import com.trucare.claims.model.Claim;
import com.trucare.claims.repository.ClaimRepository;
import com.trucare.shared.claim.ClaimDTO;
import com.trucare.shared.claim.CreateClaimDTO;
import com.trucare.shared.claim.UpdateClaimDTO;
import com.trucare.shared.document.DocumentDTO;
import jakarta.transaction.Transactional;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service("claimSync")
public class ClaimServiceImpl implements ClaimService {

    private final ClaimRepository claimRepository;
    private final ClaimMapper claimMapper;
    private final ClaimValidationService claimValidationService;

    @Autowired
    public ClaimServiceImpl(ClaimRepository claimRepository, ClaimMapper claimMapper,
                            ClaimValidationService claimValidationService) {
        this.claimRepository = claimRepository;
        this.claimMapper = claimMapper;
        this.claimValidationService = claimValidationService;
    }

    // Create or update claim reactively
    @Override
    @Transactional
    public ClaimDTO createClaim(CreateClaimDTO createClaimDTO) {

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
        Claim savedClaim = claimRepository.save(claim);

        // Return Claim DTO
        return claimMapper.toClaimDTO(savedClaim);
    }

    @Override
    public ClaimDTO getClaimById(Long claimId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ClaimNotFoundException("claim not found with id "+claimId));
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
