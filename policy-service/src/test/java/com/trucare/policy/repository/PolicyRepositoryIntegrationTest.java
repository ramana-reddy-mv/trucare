package com.trucare.policy.repository;

import com.trucare.policy.model.EnrollmentStatus;
import com.trucare.policy.model.Policy;
import com.trucare.policy.model.PolicyCoverage;
import com.trucare.policy.model.PolicyMember;
import com.trucare.policy.model.PolicyStatus;
import com.trucare.policy.model.PolicyType;
import com.trucare.policy.model.Relationship;
import com.trucare.policy.model.TreatmentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:policies;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PolicyRepositoryIntegrationTest {

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PolicyMemberRepository policyMemberRepository;

    @Test
    void persistsPolicyCoverageAndLoadsCoverageWithPolicy() {
        Policy policy = policy("POL1001");
        PolicyCoverage coverage = coverage(policy);
        policy.setCoverages(new ArrayList<>(List.of(coverage)));
        policyRepository.saveAndFlush(policy);

        var loaded = policyRepository.findWithCoveragesByPolicyNumber("POL1001").orElseThrow();

        assertEquals(1, loaded.getCoverages().size());
        assertEquals(TreatmentType.HOSPITALIZATION, loaded.getCoverages().get(0).getTreatmentType());
        assertEquals(new BigDecimal("300000.00"), loaded.getAvailableCoverageAmount());
    }

    @Test
    void databasePreventsDuplicatePolicyNumbers() {
        policyRepository.saveAndFlush(policy("POL1001"));
        assertThrows(DataIntegrityViolationException.class,
                () -> policyRepository.saveAndFlush(policy("POL1001")));
    }

    @Test
    void databasePreventsDuplicateMemberEnrollmentsForPolicy() {
        Policy policy = policy("POL1002");
        policyRepository.saveAndFlush(policy);
        policyMemberRepository.saveAndFlush(enrollment(policy, "M123456"));
        assertThrows(DataIntegrityViolationException.class,
                () -> policyMemberRepository.saveAndFlush(enrollment(policy, "M123456")));
    }

    private Policy policy(String number) {
        Policy policy = new Policy();
        policy.setPolicyNumber(number);
        policy.setPolicyName("Family Health");
        policy.setPolicyType(PolicyType.FAMILY_FLOATER);
        policy.setCoverageAmount(new BigDecimal("300000.00"));
        policy.setAvailableCoverageAmount(new BigDecimal("300000.00"));
        policy.setStartDate(LocalDate.parse("2026-01-01"));
        policy.setEndDate(LocalDate.parse("2026-12-31"));
        policy.setStatus(PolicyStatus.ACTIVE);
        return policy;
    }

    private PolicyCoverage coverage(Policy policy) {
        PolicyCoverage coverage = new PolicyCoverage();
        coverage.setPolicy(policy);
        coverage.setTreatmentType(TreatmentType.HOSPITALIZATION);
        coverage.setCoverageLimit(new BigDecimal("300000.00"));
        coverage.setWaitingPeriodDays(30);
        return coverage;
    }

    private PolicyMember enrollment(Policy policy, String memberId) {
        PolicyMember enrollment = new PolicyMember();
        enrollment.setPolicy(policy);
        enrollment.setMemberId(memberId);
        enrollment.setRelationship(Relationship.SELF);
        enrollment.setEnrollmentDate(LocalDate.parse("2026-01-01"));
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        return enrollment;
    }
}
