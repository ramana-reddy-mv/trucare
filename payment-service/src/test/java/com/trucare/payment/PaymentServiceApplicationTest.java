package com.trucare.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=jdbc:h2:mem:payment-context;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
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
