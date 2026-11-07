package com.trucare.claims.service;


import com.trucare.claims.model.Claim;
import com.trucare.shared.claim.ClaimDTO;
import com.trucare.shared.claim.CreateClaimDTO;
import com.trucare.shared.claim.UpdateClaimDTO;

import java.util.List;

public interface ClaimService {

     ClaimCreationResult createClaim(CreateClaimDTO createClaimDTO, String idempotencyKey);
     ClaimDTO getClaimById(Long claimId);
     ClaimDTO getClaimByBusinessId(String claimId);
     List<ClaimDTO> getAllClaims();
     ClaimDTO updateClaim(String claimId, UpdateClaimDTO updateClaimDTO);
     String deleteClaim(Long claimId);
}
