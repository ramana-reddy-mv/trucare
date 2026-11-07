package com.trucare.policy.repository;

import com.trucare.policy.model.EnrollmentStatus;
import com.trucare.policy.model.PolicyMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyMemberRepository extends JpaRepository<PolicyMember, Long> {

    Optional<PolicyMember> findByPolicyIdAndMemberId(Long policyId, String memberId);

    @EntityGraph(attributePaths = "policy")
    List<PolicyMember> findAllByPolicyIdOrderByMemberId(Long policyId);

    Optional<PolicyMember> findByPolicyIdAndMemberIdAndStatus(
            Long policyId, String memberId, EnrollmentStatus status);

    List<PolicyMember> findAllByMemberIdAndStatus(String memberId, EnrollmentStatus status);
}
