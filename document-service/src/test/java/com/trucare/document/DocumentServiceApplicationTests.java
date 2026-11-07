package com.trucare.document;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:postgresql://localhost:5432/trucare_documents_test",
		"spring.datasource.driver-class-name=org.postgresql.Driver",
		"spring.datasource.username=postgres",
		"spring.jpa.hibernate.ddl-auto=update",
		"eureka.client.enabled=false",
		"TRUCARE_JWT_SECRET_BASE64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class DocumentServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
