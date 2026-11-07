package com.trucare.policy.repository;

import com.trucare.policy.model.Policy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

    boolean existsByPolicyNumber(String policyNumber);

    @EntityGraph(attributePaths = "coverages")
    Optional<Policy> findWithCoveragesById(Long id);

    @EntityGraph(attributePaths = "coverages")
    Optional<Policy> findWithCoveragesByPolicyNumber(String policyNumber);

    @Query("""
            select distinct p from PolicyMember pm
            join pm.policy p
            left join fetch p.coverages
            where pm.memberId = :memberId and pm.status = com.trucare.policy.model.EnrollmentStatus.ACTIVE
            """)
    List<Policy> findAllActiveForMember(@Param("memberId") String memberId);
}
