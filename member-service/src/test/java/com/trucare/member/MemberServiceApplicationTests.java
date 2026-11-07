package com.trucare.member;

import com.trucare.member.model.Member;
import com.trucare.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/trucare_members_test",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=postgres",
        "spring.jpa.hibernate.ddl-auto=update",
        "eureka.client.enabled=false",
        "TRUCARE_JWT_SECRET_BASE64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class MemberServiceApplicationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private MemberRepository members;
    @Autowired private JwtEncoder jwtEncoder;

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
    }

    @Test
    void memberCanReadOnlyOwnProfile() throws Exception {
        mockMvc.perform(get("/api/members/M100")
                        .header("Authorization", bearer("MEMBER", "alice")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/members/M100")
                        .header("Authorization", bearer("MEMBER", "mallory")))
                .andExpect(status().isForbidden());
    }

    private String bearer(String role, String subject) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("trucare-auth-service")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim("roles", java.util.List.of(role))
                .build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
