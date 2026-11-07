package com.trucare.claims.mapper;


import com.trucare.claims.model.Claim;
import com.trucare.shared.claim.ClaimDTO;
import com.trucare.shared.claim.CreateClaimDTO;
import com.trucare.shared.claim.UpdateClaimDTO;
import com.trucare.shared.document.DocumentDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

import static org.mapstruct.NullValuePropertyMappingStrategy.IGNORE;

@Mapper(componentModel = "spring")
public interface ClaimMapper {

    @Mapping(target = "claimStatuses", source = "claimStatus")
    ClaimDTO toClaimDTO(Claim claim);

    @Mapping(target = "claimStatus", source = "claimStatuses")
    Claim toClaim(ClaimDTO claimDTO);

    @Mapping(target = "claimStatus", source = "claimStatuses")
    Claim toClaim(CreateClaimDTO createClaimDTO);

    @Mapping(target = "claimStatus", source = "claimStatuses")
    Claim toClaim(UpdateClaimDTO updateClaimDTO);

}
