package com.trucare.claims;

import com.trucare.claims.client.PolicyClient;
import com.trucare.claims.service.ClaimValidationService;
import com.trucare.shared.client.MemberClient;
import com.trucare.shared.client.ProviderClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:postgresql://localhost:5432/trucare_claims_test",
		"spring.datasource.username=postgres",
		"spring.datasource.driver-class-name=org.postgresql.Driver",
		"spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
		"spring.jpa.hibernate.ddl-auto=update",
		"eureka.client.enabled=false",
		"EUREKA_URL=http://test-eureka:8761/eureka/"
})
class ClaimsServiceApplicationTests {
	@Autowired private Environment environment;
	@Autowired private ClaimValidationService validationService;
	@MockitoBean private MemberClient memberClient;
	@MockitoBean private ProviderClient providerClient;


	@DynamicPropertySource
	static void jwtKeys(DynamicPropertyRegistry registry) {
		TestJwtKeys.configure(registry);
	}

	@Test
	void contextLoads() {
	}

	@Test
	void feignClientsUseEurekaServiceIdsAndControllerPaths() throws Exception {
		assertFeignClient(MemberClient.class, "member-service",
				"/api/members/validate/{memberId}");
		assertFeignClient(ProviderClient.class, "provider-service",
				"/api/providers/validate/{providerId}");
		assertFeignClient(PolicyClient.class, "policy-service",
				"/api/policies/eligibility/check");
		Assertions.assertEquals("http://test-eureka:8761/eureka/",
				environment.getProperty("eureka.client.service-url.defaultZone"));
	}

	@Test
	void feignTimeoutsAreConfiguredAndValidationFailsClosed() {
		Assertions.assertEquals(2000,
				environment.getProperty("spring.cloud.openfeign.client.config.member-service.connect-timeout",
						Integer.class));
		Assertions.assertEquals(3000,
				environment.getProperty("spring.cloud.openfeign.client.config.provider-service.read-timeout",
						Integer.class));
		when(memberClient.validateMember("M-FAIL")).thenThrow(new IllegalStateException("service unavailable"));
		Assertions.assertFalse(validationService.validateMember("M-FAIL"));
		verify(memberClient).validateMember("M-FAIL");
	}

	private void assertFeignClient(Class<?> clientType, String serviceId, String expectedPath)
			throws NoSuchMethodException {
		FeignClient client = clientType.getAnnotation(FeignClient.class);
		Assertions.assertNotNull(client);
		Assertions.assertEquals(serviceId, client.name());
		Assertions.assertTrue(client.url().isBlank(), "Feign client must resolve through Eureka");
		Method method = clientType.getDeclaredMethods()[0];
		Assertions.assertArrayEquals(new String[]{expectedPath},
				method.getAnnotation(GetMapping.class) != null
						? method.getAnnotation(GetMapping.class).value()
						: method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class).value());
	}
}
