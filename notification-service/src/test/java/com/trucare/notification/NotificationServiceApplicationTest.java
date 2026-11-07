package com.trucare.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "notification.security.internal-username=test-internal",
        "notification.security.internal-password=test-internal-password",
        "notification.security.admin-username=test-admin",
        "notification.security.admin-password=test-admin-password",
        "notification.delivery.enabled=false"
})
@AutoConfigureMockMvc
class NotificationServiceApplicationTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void loadsApplicationContextAndEnforcesAdminTemplateAccess() throws Exception {
        String template = """
                {
                  "templateCode": "CLAIM-TEST-EMAIL",
                  "eventType": "CLAIM_SUBMITTED",
                  "channel": "EMAIL",
                  "subjectTemplate": "Claim received",
                  "messageTemplate": "Claim {{claimId}} received",
                  "active": true
                }
                """;

        mockMvc.perform(post("/api/notification-templates")
                        .contentType(MediaType.APPLICATION_JSON).content(template))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/notification-templates")
                        .with(httpBasic("test-internal", "test-internal-password"))
                        .contentType(MediaType.APPLICATION_JSON).content(template))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/notification-templates")
                        .with(httpBasic("test-admin", "test-admin-password"))
                        .contentType(MediaType.APPLICATION_JSON).content(template))
                .andExpect(status().isOk());
    }
}
