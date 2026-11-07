package com.trucare.policy.service;

import com.trucare.policy.dto.PolicyCoverageRequest;
import com.trucare.policy.dto.PolicyCoverageResponse;
import com.trucare.policy.dto.PolicyMemberResponse;
import com.trucare.policy.dto.PolicyResponse;
import com.trucare.policy.model.Policy;
import com.trucare.policy.model.PolicyCoverage;
import com.trucare.policy.model.PolicyMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PolicyMapper {

    PolicyResponse toResponse(Policy policy);

    PolicyCoverageResponse toResponse(PolicyCoverage coverage);

    @Mapping(target = "policyId", source = "policy.id")
    @Mapping(target = "policyNumber", source = "policy.policyNumber")
    PolicyMemberResponse toResponse(PolicyMember enrollment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "policy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PolicyCoverage toEntity(PolicyCoverageRequest request);

    default void replaceCoverages(Policy policy, List<PolicyCoverageRequest> requests) {
        policy.getCoverages().clear();
        requests.forEach(request -> {
            PolicyCoverage coverage = toEntity(request);
            coverage.setPolicy(policy);
            policy.getCoverages().add(coverage);
        });
    }
}
