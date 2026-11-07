package com.trucare.claims.controller;


import com.trucare.claims.model.Claim;
import com.trucare.shared.claim.ClaimDTO;
import com.trucare.shared.claim.CreateClaimDTO;
import com.trucare.shared.claim.UpdateClaimDTO;
import com.trucare.claims.service.ClaimCreationResult;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import com.trucare.claims.service.ClaimService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {


    private final ClaimService claimService;
    private  static final Logger logger = LoggerFactory.getLogger(ClaimController.class);

    @Autowired
    public ClaimController(@Qualifier("claimSync") ClaimService claimService) {
        this.claimService = claimService;
    }

    // Endpoint to create or update claim
    @PostMapping
    public ResponseEntity<ClaimDTO> createClaim(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateClaimDTO createClaimDTO) {
        ClaimCreationResult result = claimService.createClaim(createClaimDTO, idempotencyKey);
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return new ResponseEntity<>(result.claim(), status);
    }

    @GetMapping("/getClaim/{claimId}")
    public ResponseEntity<ClaimDTO> getClaim(@PathVariable Long claimId){
        ClaimDTO claimDTO = claimService.getClaimById(claimId);
        return new ResponseEntity<>(claimDTO,HttpStatus.FOUND);
    }

    @GetMapping("/by-claim-id/{claimId}")
    public ResponseEntity<ClaimDTO> getClaimByBusinessId(@PathVariable String claimId) {
        return ResponseEntity.ok(claimService.getClaimByBusinessId(claimId));
    }

    @GetMapping("/removeClaim/{claimId}")
    public ResponseEntity<String> removeClaim(@PathVariable Long claimId){
        String res = claimService.deleteClaim(claimId);
        return new ResponseEntity<>(res,HttpStatus.NO_CONTENT);
    }

    @PutMapping("/updateClaim/{claimId}")
    public ResponseEntity<ClaimDTO> updateClaim(@PathVariable String claimId, @RequestBody UpdateClaimDTO updateClaimDTO) {
        ClaimDTO createdClaim = claimService.updateClaim(claimId, updateClaimDTO);
        return new ResponseEntity<>(createdClaim,HttpStatus.OK);
    }
}
