package com.trucare.provider;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:postgresql://localhost:5432/trucare_providers_test",
		"spring.datasource.username=postgres",
		"spring.jpa.hibernate.ddl-auto=update",
		"eureka.client.enabled=false"
})
class ClaimsProcessingServiceApplicationTests {
	@Autowired private MockMvc mockMvc;

	@DynamicPropertySource
	static void jwtKeys(DynamicPropertyRegistry registry) {
		TestJwtKeys.configure(registry);
	}

	@Test
	void contextLoads() {
	}

	@Test
	void serviceCredentialsAuthorizeProviderValidation() throws Exception {
		mockMvc.perform(get("/api/providers/validate/MISSING"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/providers/validate/MISSING")
						.header("Authorization", basicServiceCredentials()))
				.andExpect(status().isOk());
	}

	private String basicServiceCredentials() {
		String encoded = java.util.Base64.getEncoder().encodeToString(
				"test-service:test-service-password".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		return "Basic " + encoded;
	}

}
