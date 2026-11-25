package com.trucare.claims.controller;


import com.trucare.claims.model.Claim;
import com.trucare.shared.claim.ClaimDTO;
import com.trucare.claims.dto.CreateClaimRequest;
import com.trucare.claims.dto.ChangeClaimStatusRequest;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.time.LocalDate;
import com.trucare.shared.claim.ClaimPageDTO;
import com.trucare.shared.claim.ClaimDashboardSummaryDTO;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {


    private final ClaimService claimService;
    private  static final Logger logger = LoggerFactory.getLogger(ClaimController.class);

    @Autowired
    public ClaimController(@Qualifier("claimSync") ClaimService claimService) {
        this.claimService = claimService;
    }

    @GetMapping
    public ResponseEntity<ClaimPageDTO<ClaimDTO>> searchClaims(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String memberId,
            @RequestParam(required = false) String providerId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String claimId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page must be non-negative and size must be between 1 and 100");
        }
        String[] sortParts = sort.split(",", -1);
        if (sortParts.length != 2 || !java.util.Set.of("createdAt", "updatedAt", "claimId", "claimAmount", "treatmentDate").contains(sortParts[0])
                || !java.util.Set.of("asc", "desc").contains(sortParts[1].toLowerCase(java.util.Locale.ROOT))) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortParts[1]), sortParts[0]));
        return ResponseEntity.ok(claimService.searchClaims(status, memberId, providerId, from, to, claimId, pageable));
    }

    @GetMapping("/dashboard/summary")
    public ResponseEntity<ClaimDashboardSummaryDTO> dashboardSummary() {
        return ResponseEntity.ok(claimService.dashboardSummary());
    }

    @GetMapping("/{claimId}")
    public ResponseEntity<ClaimDTO> getClaim(@PathVariable String claimId) {
        return ResponseEntity.ok(claimService.getClaimByBusinessId(claimId));
    }

    // Endpoint to create or update claim
    @PostMapping
    public ResponseEntity<ClaimDTO> createClaim(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateClaimRequest createClaimDTO) {
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

    @PutMapping("/{claimId}/status")
    public ResponseEntity<ClaimDTO> transitionClaimStatus(
            @PathVariable String claimId,
            @Valid @RequestBody ChangeClaimStatusRequest request) {
        return ResponseEntity.ok(claimService.transitionClaimStatus(claimId, request));
    }
}
