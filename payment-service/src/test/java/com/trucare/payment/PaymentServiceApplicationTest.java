package com.trucare.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "TRUCARE_JWT_SECRET_BASE64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
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

    @Test
    void loadsApplicationContext() {
    }
}
