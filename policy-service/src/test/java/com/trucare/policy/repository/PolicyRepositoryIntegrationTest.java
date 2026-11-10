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
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/trucare_policies_test",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=postgres",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "EUREKA_URL=http://test-eureka:8761/eureka/"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PolicyRepositoryIntegrationTest {
    @DynamicPropertySource
    static void postgresPassword(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.password", PolicyRepositoryIntegrationTest::localDatabasePassword);
    }


    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PolicyMemberRepository policyMemberRepository;
    @Autowired
    private Environment environment;

    @Test
    void memberFeignConnectionTimeoutAndEurekaAddressAreConfigured() {
        assertEquals(2000, environment.getProperty(
                "spring.cloud.openfeign.client.config.member-service.connect-timeout", Integer.class));
        assertEquals(3000, environment.getProperty(
                "spring.cloud.openfeign.client.config.member-service.read-timeout", Integer.class));
        assertEquals("http://test-eureka:8761/eureka/",
                environment.getProperty("eureka.client.service-url.defaultZone"));
    }

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

    private static String localDatabasePassword() {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(Path.of("../local-secrets.properties"))) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load local PostgreSQL test credentials", exception);
        }
        String password = properties.getProperty("spring.datasource.password");
        if (password == null) {
            throw new IllegalStateException("spring.datasource.password is missing from local-secrets.properties");
        }
        return password;
    }
}
