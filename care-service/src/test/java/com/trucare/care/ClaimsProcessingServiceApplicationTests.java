package com.trucare.care;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:postgresql://localhost:5432/trucare_care_test",
		"spring.datasource.username=postgres",
		"spring.jpa.hibernate.ddl-auto=update",
		"eureka.client.enabled=false"
})
class ClaimsProcessingServiceApplicationTests {

	@DynamicPropertySource
	static void jwtKeys(DynamicPropertyRegistry registry) {
		TestJwtKeys.configure(registry);
	}

	@Test
	void contextLoads() {
	}

}
