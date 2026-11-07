package com.trucare.auth_service;

import com.trucare.auth_service.respository.UserCredentialRepository;
import com.trucare.auth_service.service.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/trucare_auth_test",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=postgres",
        "spring.jpa.hibernate.ddl-auto=update",
        "eureka.client.enabled=false",
        "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.expiration-seconds=900"
})
@AutoConfigureMockMvc
class AuthServiceApplicationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserCredentialRepository users;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    @Test
    void registrationPersistsOnlyAnEncodedPasswordAndReturnsSafeUserResponse() throws Exception {
        String username = unique("test.member");
        String email = unique("member") + "@example.test";
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(username, email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("MEMBER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        var stored = users.findByNameIgnoreCaseOrEmailIgnoreCase(username, username).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotEquals("correct-horse-battery", stored.getPassword());
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches(
                "correct-horse-battery", stored.getPassword()));
    }

    @Test
    void duplicateUsernameOrEmailIsRejectedWithoutReturningCredentials() throws Exception {
        String username = unique("test.member");
        String email = unique("member") + "@example.test";
        mockMvc.perform(post("/auth/addUser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(username, email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(username.toUpperCase(), unique("other") + "@example.test")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(unique("other.member"), email.toUpperCase())))
                .andExpect(status().isConflict());
    }

    @Test
    void loginAuthenticatesPasswordAndReturnsJwtOnlyOnSuccess() throws Exception {
        String username = unique("test.member");
        String email = unique("member") + "@example.test";
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(username, email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrEmail":"%s","password":"correct-horse-battery"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrEmail":"%s","password":"wrong-password"}
                                """.formatted(username)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void jwtValidationRejectsExpiredAndWrongSignatureTokens() {
        String expired = jwtService.createToken("test.member", java.util.List.of("MEMBER"), -60);
        assertThrows(ExpiredJwtException.class, () -> jwtService.validateToken(expired));

        JwtService wrongKey = new JwtService(
                "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXphYmNkZWY=", "trucare-auth-service");
        String token = jwtService.createToken("test.member", java.util.List.of("MEMBER"), 900);
        assertThrows(JwtException.class, () -> wrongKey.validateToken(token));
    }

    private String registration(String name, String email) {
        return """
                {"name":"%s","email":"%s","password":"correct-horse-battery"}
                """.formatted(name, email);
    }

    private String unique(String prefix) {
        return prefix + "." + java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
