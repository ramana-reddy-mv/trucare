package com.trucare.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/trucare_payments_test",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=postgres",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "payment.security.operator-username=test-operator",
        "payment.security.operator-password=test-operator-password",
        "payment.security.internal-username=test-internal",
        "payment.security.internal-password=test-internal-password",
        "settlement.client.username=test-settlement",
        "settlement.client.password=test-settlement-password",
        "payment.callback.retry.enabled=false"
})
class PaymentServiceApplicationTest {

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.configure(registry);
    }

    @Test
    void loadsApplicationContext() {
    }
}
