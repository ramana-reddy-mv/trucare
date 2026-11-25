package com.trucare.claims.mapper;


import com.trucare.claims.model.Claim;
import com.trucare.shared.claim.ClaimDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClaimMapper {

    @Mapping(target = "claimStatuses", source = "claimStatus")
    ClaimDTO toClaimDTO(Claim claim);

}
