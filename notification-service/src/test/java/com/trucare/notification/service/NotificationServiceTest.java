package com.trucare.notification.service;

import com.trucare.notification.dto.CreateNotificationRequest;
import com.trucare.notification.dto.NotificationResponse;
import com.trucare.notification.exception.NotificationException;
import com.trucare.notification.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock private NotificationStore store;
    @Mock private NotificationTemplateService templates;
    @Mock private NotificationFingerprint fingerprint;

    private NotificationService service;
    private CreateNotificationRequest request;
    private NotificationTemplate template;
    private NotificationResponse response;

    @BeforeEach
    void setUp() {
        service = new NotificationService(store, templates, new TemplateRenderer(), fingerprint, 4);
        request = new CreateNotificationRequest(NotificationEventType.CLAIM_SUBMITTED, "501",
                RecipientType.MEMBER, NotificationChannel.EMAIL, "member@example.com",
                RelatedEntityType.CLAIM, "CLM1001", null, null, null,
                Map.of("memberName", "Ram", "claimId", "CLM1001"));
        template = new NotificationTemplate();
        template.setId(8L);
        template.setTemplateCode("CLAIM-SUBMITTED-EMAIL");
        template.setEventType(NotificationEventType.CLAIM_SUBMITTED);
        template.setChannel(NotificationChannel.EMAIL);
        template.setActive(true);
        template.setSubjectTemplate("Claim {{claimId}} received");
        template.setMessageTemplate("Hello {{memberName}}");
        response = new NotificationResponse(1L, "NTF-1", request.eventType(), request.recipientId(),
                request.recipientType(), request.recipientAddress(), request.channel(), "Claim received",
                "Hello Ram", request.relatedEntityType(), request.relatedEntityId(), NotificationStatus.PENDING,
                null, null, null, template.getTemplateCode(), 8L, null, 0, 4, null, null, java.util.List.of());
        lenient().when(fingerprint.calculate(request)).thenReturn("hash");
    }

    @Test
    void createsNotificationFromActiveTemplateAndPersistsItAsynchronously() {
        when(store.existingForKey("key", "hash")).thenReturn(null);
        when(store.existingForBusinessDedup(any(), anyString(), any(), isNull())).thenReturn(null);
        when(templates.resolve(null, request.eventType(), request.channel())).thenReturn(template);
        when(store.isEnabled("501", RecipientType.MEMBER, NotificationChannel.EMAIL)).thenReturn(true);
        when(store.create(any(Notification.class))).thenReturn(response);

        assertFalse(service.create(request, "key").replayed());
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(store).create(captor.capture());
        assertEquals(NotificationStatus.PENDING, captor.getValue().getNotificationStatus());
        assertEquals("Hello Ram", captor.getValue().getMessage());
        assertEquals("Claim CLM1001 received", captor.getValue().getSubject());
    }

    @Test
    void returnsOriginalNotificationForSameIdempotencyKey() {
        when(store.existingForKey("key", "hash")).thenReturn(response);

        NotificationService.CreationResult result = service.create(request, "key");

        assertTrue(result.replayed());
        assertSame(response, result.notification());
        verifyNoInteractions(templates);
    }

    @Test
    void differentPayloadWithExistingIdempotencyKeyConflicts() {
        when(fingerprint.calculate(request)).thenReturn("different-hash");
        when(store.existingForKey("key", "different-hash"))
                .thenThrow(new NotificationException(org.springframework.http.HttpStatus.CONFLICT,
                        "Idempotency-Key was already used with a different request"));

        assertThrows(NotificationException.class, () -> service.create(request, "key"));
        verifyNoInteractions(templates);
    }

    @Test
    void businessDeduplicationReturnsOriginalEventNotification() {
        when(store.existingForKey("key", "hash")).thenReturn(null);
        when(store.existingForBusinessDedup(request.eventType(), request.recipientId(),
                request.channel(), null)).thenReturn(response);

        NotificationService.CreationResult result = service.create(request, "key");

        assertTrue(result.replayed());
        assertSame(response, result.notification());
        verifyNoInteractions(templates);
    }

    @Test
    void disabledOptionalPreferenceCancelsWithoutDispatch() {
        when(store.existingForKey("key", "hash")).thenReturn(null);
        when(store.existingForBusinessDedup(any(), anyString(), any(), isNull())).thenReturn(null);
        when(templates.resolve(null, request.eventType(), request.channel())).thenReturn(template);
        when(store.isEnabled("501", RecipientType.MEMBER, NotificationChannel.EMAIL)).thenReturn(false);
        when(store.create(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            assertEquals(NotificationStatus.CANCELLED, notification.getNotificationStatus());
            return response;
        });

        service.create(request, "key");

        verify(store).create(any(Notification.class));
    }

    @Test
    void mandatoryTransactionalEventIgnoresDisabledPreference() {
        CreateNotificationRequest mandatory = new CreateNotificationRequest(NotificationEventType.PAYMENT_SUCCESS,
                "501", RecipientType.MEMBER, NotificationChannel.EMAIL, "member@example.com",
                RelatedEntityType.PAYMENT, "PAY1", null, null, null, Map.of());
        when(fingerprint.calculate(mandatory)).thenReturn("mandatory-hash");
        when(store.existingForKey("key", "mandatory-hash")).thenReturn(null);
        when(store.existingForBusinessDedup(any(), anyString(), any(), isNull())).thenReturn(null);
        template.setEventType(NotificationEventType.PAYMENT_SUCCESS);
        template.setSubjectTemplate("Payment update");
        template.setMessageTemplate("Payment complete");
        when(templates.resolve(null, mandatory.eventType(), mandatory.channel())).thenReturn(template);
        when(store.create(any(Notification.class))).thenReturn(response);

        service.create(mandatory, "key");

        verify(store, never()).isEnabled(anyString(), any(), any());
        verify(store).create(argThat(notification ->
                notification.getNotificationStatus() == NotificationStatus.PENDING));
    }

    @Test
    void missingTemplateIsReportedRatherThanReturningSuccess() {
        when(store.existingForKey("key", "hash")).thenReturn(null);
        when(store.existingForBusinessDedup(any(), anyString(), any(), isNull())).thenReturn(null);
        when(templates.resolve(null, request.eventType(), request.channel()))
                .thenThrow(new NotificationException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "No active notification template exists for this event and channel"));

        assertThrows(NotificationException.class, () -> service.create(request, "key"));
        verify(store, never()).create(any());
    }
}
