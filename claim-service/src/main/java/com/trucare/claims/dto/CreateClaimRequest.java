package com.trucare.claims.dto;

import com.trucare.shared.claim.CreateClaimDTO;
import com.trucare.claims.validation.ValidClaimAmount;

import java.math.BigDecimal;

/** HTTP create payload, with the Claim Service specific amount constraint. */
public class CreateClaimRequest extends CreateClaimDTO {
    @Override
    @ValidClaimAmount
    public BigDecimal getClaimAmount() {
        return super.getClaimAmount();
    }
}
