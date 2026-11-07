package com.trucare.settlement.client;

import com.trucare.shared.claim.ClaimDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "claims-service", contextId = "settlementClaimClient")
public interface ClaimClient {

    @GetMapping("/api/claims/by-claim-id/{claimId}")
    ClaimDTO getClaimByBusinessId(@PathVariable("claimId") String claimId);
}
