package com.trucare.notification.service;

import com.trucare.notification.dto.NotificationResponse;
import com.trucare.notification.model.*;
import com.trucare.notification.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/trucare_notifications_persistence_test",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=postgres",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(NotificationStore.class)
class NotificationPersistenceTest {
    @DynamicPropertySource
    static void postgresPassword(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.password", NotificationPersistenceTest::localDatabasePassword);
    }

    @Autowired private NotificationStore store;
    @Autowired private NotificationRepository notifications;
    @Autowired private NotificationAttemptRepository attempts;
    @Autowired private EntityManager entityManager;

    @Test
    void persistsNotificationWithUniqueIdempotencyKey() {
        NotificationResponse response = store.create(notification("key-1"));

        assertNotNull(response.id());
        assertEquals(NotificationStatus.PENDING, response.notificationStatus());
        assertThrows(DataIntegrityViolationException.class,
                () -> store.create(notification("key-1")));
    }

    @Test
    void databasePreventsDuplicateBusinessNotificationForSameEventRecipientAndChannel() {
        Notification first = notification("key-business-1");
        first.setBusinessDeduplicationReference("CLAIM-1-APPROVED");
        store.create(first);
        Notification duplicate = notification("key-business-2");
        duplicate.setBusinessDeduplicationReference("CLAIM-1-APPROVED");

        assertThrows(DataIntegrityViolationException.class, () -> store.create(duplicate));
    }

    @Test
    void competingWorkersCannotClaimTheSameNotificationTwice() {
        NotificationResponse created = store.create(notification("key-claim"));

        NotificationStore.DeliveryWork first = store.claim(created.id());
        NotificationStore.DeliveryWork second = store.claim(created.id());

        assertNotNull(first);
        assertEquals(1, first.attemptNumber());
        assertNull(second);
        assertEquals(1, attempts.findByNotificationIdOrderByAttemptNumberAsc(created.id()).size());
        assertEquals(NotificationStatus.PROCESSING,
                notifications.findById(created.id()).orElseThrow().getNotificationStatus());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentWorkersOnlyCreateOneProcessingAttempt() throws Exception {
        NotificationResponse created = store.create(notification("key-concurrent"));
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            List<Future<NotificationStore.DeliveryWork>> results = workers.invokeAll(List.of(
                    () -> store.claim(created.id()),
                    () -> store.claim(created.id())));
            long claims = 0;
            for (Future<NotificationStore.DeliveryWork> result : results) {
                if (result.get(5, TimeUnit.SECONDS) != null) claims++;
            }
            assertEquals(1, claims);
            assertEquals(1, attempts.findByNotificationIdOrderByAttemptNumberAsc(created.id()).size());
        } finally {
            workers.shutdownNow();
            attempts.deleteAll();
            notifications.deleteById(created.id());
        }
    }

    @Test
    void failedAttemptCanBeRetriedUntilConfiguredMaximum() {
        NotificationResponse created = store.create(notification("key-retry"));
        NotificationStore.DeliveryWork first = store.claim(created.id());
        store.complete(created.id(), first.attemptNumber(), null, "provider unavailable", 0);

        NotificationResponse queuedRetry = store.retry(created.id());
        assertEquals(NotificationStatus.FAILED, queuedRetry.notificationStatus());
        NotificationStore.DeliveryWork retry = store.claim(created.id());
        assertNotNull(retry);
        assertEquals(2, retry.attemptNumber());
        assertEquals(2, attempts.findByNotificationIdOrderByAttemptNumberAsc(created.id()).size());
    }

    @Test
    void staleProcessingIsRecoveredAsFailedAttempt() {
        NotificationResponse created = store.create(notification("key-stale"));
        NotificationStore.DeliveryWork work = store.claim(created.id());
        Instant cutoff = Instant.now().minusSeconds(10);
        entityManager.createNativeQuery("update notifications set updated_at = :updatedAt where id = :id")
                .setParameter("updatedAt", cutoff.minusSeconds(1))
                .setParameter("id", created.id())
                .executeUpdate();
        entityManager.clear();

        store.recoverStale(created.id(), cutoff);

        Notification recovered = notifications.findById(created.id()).orElseThrow();
        assertEquals(NotificationStatus.FAILED, recovered.getNotificationStatus());
        assertEquals(AttemptStatus.FAILED, attempts.findByNotificationIdAndAttemptNumber(
                created.id(), work.attemptNumber()).orElseThrow().getAttemptStatus());
    }

    @Test
    void readStatusIsIndependentFromDeliveryStatusAndRestrictedToInApp() {
        Notification inApp = notification("key-in-app");
        inApp.setChannel(NotificationChannel.IN_APP);
        inApp.setRecipientAddress(null);
        NotificationResponse created = store.create(inApp);

        NotificationResponse read = store.markRead(created.id(), "recipient-1", false);

        assertNotNull(read.readAt());
        assertEquals(NotificationStatus.PENDING, read.notificationStatus());
        assertThrows(RuntimeException.class, () -> store.markRead(created.id(), "someone-else", false));
    }

    @Test
    void unreadInAppPaginationAndPreferenceUpdatesArePersistent() {
        Notification inApp = notification("key-unread");
        inApp.setChannel(NotificationChannel.IN_APP);
        inApp.setRecipientAddress(null);
        NotificationResponse created = store.create(inApp);
        store.savePreference("recipient-1", RecipientType.MEMBER, NotificationChannel.SMS, false);

        assertEquals(1, store.getInApp("recipient-1", true, PageRequest.of(0, 10)).getTotalElements());
        assertFalse(store.isEnabled("recipient-1", RecipientType.MEMBER, NotificationChannel.SMS));
        assertTrue(store.isEnabled("recipient-1", RecipientType.MEMBER, NotificationChannel.EMAIL));
        store.markRead(created.id(), "recipient-1", false);
        assertEquals(0, store.getInApp("recipient-1", true, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, store.getInApp("recipient-1", false, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, store.getPreferences("recipient-1", RecipientType.MEMBER).size());
    }

    @Test
    void supportsPaginatedSearchByEventChannelAndRelatedEntity() {
        Notification first = notification("key-page-1");
        first.setChannel(NotificationChannel.IN_APP);
        first.setRecipientAddress(null);
        first.setRelatedEntityId("claim-page-1");
        store.create(first);
        Notification second = notification("key-page-2");
        second.setChannel(NotificationChannel.IN_APP);
        second.setRecipientAddress(null);
        second.setRelatedEntityId("claim-page-2");
        store.create(second);

        var result = store.search(NotificationEventType.CLAIM_SUBMITTED, NotificationChannel.IN_APP,
                NotificationStatus.PENDING, "recipient-1", RelatedEntityType.CLAIM, null,
                null, null, PageRequest.of(0, 1));

        assertEquals(2, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }

    @Test
    void recoversMaximumAttemptAsDeadEndWithoutRetryScheduling() {
        Notification notification = notification("key-max");
        notification.setMaxAttempts(1);
        NotificationResponse created = store.create(notification);
        NotificationStore.DeliveryWork work = store.claim(created.id());
        store.complete(created.id(), work.attemptNumber(), null, "provider rejected", 0);

        Notification failed = notifications.findById(created.id()).orElseThrow();
        assertEquals(NotificationStatus.FAILED, failed.getNotificationStatus());
        assertNull(failed.getNextAttemptAt());
        assertThrows(RuntimeException.class, () -> store.retry(created.id()));
    }

    private Notification notification(String idempotencyKey) {
        Notification notification = new Notification();
        notification.setNotificationReference("NTF-" + idempotencyKey);
        notification.setEventType(NotificationEventType.CLAIM_SUBMITTED);
        notification.setRecipientId("recipient-1");
        notification.setRecipientType(RecipientType.MEMBER);
        notification.setRecipientAddress("recipient@example.com");
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setSubject("Subject");
        notification.setMessage("Body");
        notification.setRelatedEntityType(RelatedEntityType.CLAIM);
        notification.setRelatedEntityId("claim-1");
        notification.setNotificationStatus(NotificationStatus.PENDING);
        notification.setIdempotencyKey(idempotencyKey);
        notification.setRequestFingerprint("fingerprint-" + idempotencyKey);
        notification.setTemplateCode("template");
        notification.setMaxAttempts(3);
        notification.setScheduledAt(Instant.now().minusSeconds(1));
        return notification;
    }

    private static String localDatabasePassword() {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(Path.of("../local-secrets.properties"))) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load local PostgreSQL test credentials", exception);
        }
        String password = properties.getProperty("spring.datasource.password");
        if (password == null) {
            throw new IllegalStateException("spring.datasource.password is missing from local-secrets.properties");
        }
        return password;
    }
}
