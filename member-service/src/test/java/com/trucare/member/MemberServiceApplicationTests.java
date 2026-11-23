package com.trucare.member;

import com.trucare.member.model.Member;
import com.trucare.member.repository.MemberRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/trucare_members_test",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=postgres",
        "spring.jpa.hibernate.ddl-auto=update",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class MemberServiceApplicationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private MemberRepository members;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.configure(registry);
    }

    @BeforeEach
    void resetMember() {
        members.deleteAll();
        Member member = new Member();
        member.setMemberId("M100");
        member.setUsername("alice");
        member.setFirstName("Alice");
        member.setLastName("Example");
        member.setGender("OTHER");
        members.save(member);
    }

    @Test
    void directRequestsRequireJwtAndServiceRoleIsRequiredForValidation() throws Exception {
        mockMvc.perform(get("/api/members"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/members/validate/M100")
                        .header("Authorization", bearer("MEMBER", "alice")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/members/validate/M100")
                        .header("Authorization", bearer("SERVICE", "service:claim-service")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/members/validate/M100")
                        .header("Authorization", basicServiceCredentials()))
                .andExpect(status().isOk());
    }

    @Test
    void memberCanReadOnlyOwnProfile() throws Exception {
        mockMvc.perform(get("/api/members/M100")
                        .header("Authorization", bearer("MEMBER", "alice")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/members/M100")
                        .header("Authorization", bearer("MEMBER", "mallory")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/members/M100")
                        .header("Authorization", bearer("PENDING_MEMBER", "alice")))
                .andExpect(status().isForbidden());
    }

    @Test
    void pendingMemberCanCreateOnlyOwnProfileWithoutSuppliedDatabaseIds() throws Exception {
        Long existingId = members.findByMemberId("M100").orElseThrow().getId();
        mockMvc.perform(post("/api/members/addMember")
                        .header("Authorization", bearer("PENDING_MEMBER", "new-member"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":%d,"username":"mallory","firstName":"New","lastName":"Member",
                                 "gender":"OTHER","documentIds":[123]}
                                """.formatted(existingId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("new-member"))
                .andExpect(jsonPath("$.documentIds").isEmpty());

        Assertions.assertEquals(2, members.count());
        Assertions.assertEquals("Alice", members.findByMemberId("M100").orElseThrow().getFirstName());
        Assertions.assertEquals("new-member", members.findByUsername("new-member").orElseThrow().getUsername());
    }

    @Test
    void correlationIdAndPrometheusEndpointAreAvailableWithoutExposingOtherActuatorData() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("X-Correlation-ID", "member-check-42"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "member-check-42"));

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_memory_used_bytes")));

        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    private String bearer(String role, String subject) {
        return "Bearer " + TestJwtKeys.token(role, subject);
    }

    private String basicServiceCredentials() {
        String encoded = Base64.getEncoder().encodeToString(
                "test-service:test-service-password".getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
