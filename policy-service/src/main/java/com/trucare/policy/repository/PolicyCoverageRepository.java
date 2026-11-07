package com.trucare.policy.repository;

import com.trucare.policy.model.PolicyCoverage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyCoverageRepository extends JpaRepository<PolicyCoverage, Long> {

    List<PolicyCoverage> findAllByPolicyIdIn(List<Long> policyIds);
}
