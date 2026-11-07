package com.trucare.notification.service;

import com.trucare.notification.dto.*;
import com.trucare.notification.exception.NotificationException;
import com.trucare.notification.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class NotificationService {
    private static final Set<NotificationEventType> MANDATORY_EVENTS = Set.of(
            NotificationEventType.CLAIM_APPROVED,
            NotificationEventType.CLAIM_REJECTED,
            NotificationEventType.CLAIM_REQUIRES_DOCUMENTS,
            NotificationEventType.SETTLEMENT_COMPLETED,
            NotificationEventType.SETTLEMENT_FAILED,
            NotificationEventType.PAYMENT_SUCCESS,
            NotificationEventType.PAYMENT_FAILED);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[1-9][0-9]{6,14}$");

    private final NotificationStore store;
    private final NotificationTemplateService templates;
    private final TemplateRenderer renderer;
    private final NotificationFingerprint fingerprint;
    private final int maxAttempts;

    public NotificationService(NotificationStore store, NotificationTemplateService templates,
                               TemplateRenderer renderer,
                               NotificationFingerprint fingerprint,
                               @Value("${notification.delivery.max-attempts:5}") int maxAttempts) {
        this.store = store;
        this.templates = templates;
        this.renderer = renderer;
        this.fingerprint = fingerprint;
        if (maxAttempts < 1) {
            throw new IllegalStateException("Notification delivery max attempts must be at least one");
        }
        this.maxAttempts = maxAttempts;
    }

    public CreationResult create(CreateNotificationRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new NotificationException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must contain 1 to 128 characters");
        }
        validateRequest(request);
        String hash = fingerprint.calculate(request);
        NotificationResponse existing = store.existingForKey(idempotencyKey, hash);
        if (existing != null) return new CreationResult(existing, true);

        NotificationResponse businessDuplicate = store.existingForBusinessDedup(request.eventType(),
                request.recipientId(), request.channel(), request.businessDeduplicationReference());
        if (businessDuplicate != null) return new CreationResult(businessDuplicate, true);

        var template = templates.resolve(request.templateCode(), request.eventType(), request.channel());
        Map<String, String> variables = request.variables() == null ? Map.of() : request.variables();
        Notification notification = new Notification();
        notification.setNotificationReference("NTF-" + UUID.randomUUID().toString().replace("-", ""));
        notification.setEventType(request.eventType());
        notification.setRecipientId(request.recipientId());
        notification.setRecipientType(request.recipientType());
        notification.setRecipientAddress(request.recipientAddress());
        notification.setChannel(request.channel());
        String subject = template.getSubjectTemplate() == null ? null
                : renderer.render(template.getSubjectTemplate(), variables);
        if (request.channel() == NotificationChannel.EMAIL && (subject == null || subject.isBlank())) {
            throw new NotificationException(HttpStatus.CONFLICT, "Email template must define a subject");
        }
        notification.setSubject(subject);
        notification.setMessage(renderer.render(template.getMessageTemplate(), variables));
        notification.setRelatedEntityType(request.relatedEntityType());
        notification.setRelatedEntityId(request.relatedEntityId());
        notification.setNotificationStatus(NotificationStatus.PENDING);
        notification.setScheduledAt(request.scheduledAt() == null ? Instant.now() : request.scheduledAt());
        notification.setIdempotencyKey(idempotencyKey);
        notification.setRequestFingerprint(hash);
        notification.setBusinessDeduplicationReference(request.businessDeduplicationReference());
        notification.setTemplateCode(template.getTemplateCode());
        notification.setTemplateId(template.getId());
        notification.setMaxAttempts(maxAttempts);
        if (!MANDATORY_EVENTS.contains(request.eventType())
                && !store.isEnabled(request.recipientId(), request.recipientType(), request.channel())) {
            notification.setNotificationStatus(NotificationStatus.CANCELLED);
            notification.setFailureReason("Channel disabled by recipient preference");
        }
        try {
            return new CreationResult(store.create(notification), false);
        } catch (DataIntegrityViolationException exception) {
            NotificationResponse winner = store.existingForKey(idempotencyKey, hash);
            if (winner == null) {
                winner = store.existingForBusinessDedup(request.eventType(), request.recipientId(),
                        request.channel(), request.businessDeduplicationReference());
            }
            if (winner != null) return new CreationResult(winner, true);
            throw new NotificationException(HttpStatus.CONFLICT,
                    "Notification conflicts with a concurrently created request");
        }
    }

    public NotificationResponse get(Long id) {
        return store.get(id);
    }

    public NotificationResponse getByReference(String reference) {
        return store.getByReference(reference);
    }

    public Page<NotificationResponse> getForRecipient(String recipientId, Pageable pageable) {
        return store.getForRecipient(recipientId, pageable);
    }

    public Page<NotificationResponse> getInApp(String recipientId, boolean unreadOnly, Pageable pageable) {
        return store.getInApp(recipientId, unreadOnly, pageable);
    }

    public Page<NotificationResponse> search(NotificationEventType eventType, NotificationChannel channel,
                                              NotificationStatus status, String recipientId,
                                              RelatedEntityType relatedEntityType, String relatedEntityId,
                                              Instant from, Instant to, Pageable pageable) {
        return store.search(eventType, channel, status, recipientId, relatedEntityType, relatedEntityId,
                from, to, pageable);
    }

    public NotificationResponse markRead(Long id, String principal, boolean privileged) {
        return store.markRead(id, principal, privileged);
    }

    public NotificationResponse retry(Long id) {
        return store.retry(id);
    }

    private void validateRequest(CreateNotificationRequest request) {
        RelatedEntityType expected = request.eventType().name().startsWith("CLAIM_") ? RelatedEntityType.CLAIM
                : request.eventType().name().startsWith("SETTLEMENT_") ? RelatedEntityType.SETTLEMENT
                : RelatedEntityType.PAYMENT;
        if (request.relatedEntityType() != expected) {
            throw new NotificationException(HttpStatus.BAD_REQUEST,
                    "Related entity type does not match notification event type");
        }
        if (request.channel() == NotificationChannel.EMAIL
                && (request.recipientAddress() == null || !EMAIL.matcher(request.recipientAddress()).matches())) {
            throw new NotificationException(HttpStatus.BAD_REQUEST, "A valid email address is required");
        }
        if (request.channel() == NotificationChannel.SMS
                && (request.recipientAddress() == null || !PHONE.matcher(request.recipientAddress()).matches())) {
            throw new NotificationException(HttpStatus.BAD_REQUEST, "A valid phone number in E.164 format is required");
        }
        if (request.scheduledAt() != null && request.scheduledAt().isBefore(Instant.now().minusSeconds(60))) {
            throw new NotificationException(HttpStatus.BAD_REQUEST, "scheduledAt cannot be in the past");
        }
    }

    public record CreationResult(NotificationResponse notification, boolean replayed) {
    }
}
