package com.trucare.notification.service;

import com.trucare.notification.dto.*;
import com.trucare.notification.exception.NotificationException;
import com.trucare.notification.model.*;
import com.trucare.notification.provider.ProviderResult;
import com.trucare.notification.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class NotificationStore {
    private final NotificationRepository notifications;
    private final NotificationAttemptRepository attempts;
    private final NotificationPreferenceRepository preferences;

    public NotificationStore(NotificationRepository notifications, NotificationAttemptRepository attempts,
                              NotificationPreferenceRepository preferences) {
        this.notifications = notifications;
        this.attempts = attempts;
        this.preferences = preferences;
    }

    @Transactional
    public NotificationResponse create(Notification notification) {
        Notification saved = notifications.saveAndFlush(notification);
        return response(saved);
    }

    @Transactional(readOnly = true)
    public NotificationResponse get(Long id) {
        return response(notifications.findById(id).orElseThrow(
                () -> new NotificationException(HttpStatus.NOT_FOUND, "Notification was not found")));
    }

    @Transactional(readOnly = true)
    public NotificationResponse getByReference(String reference) {
        return response(notifications.findByNotificationReference(reference).orElseThrow(
                () -> new NotificationException(HttpStatus.NOT_FOUND, "Notification was not found")));
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getForRecipient(String recipientId, Pageable pageable) {
        return notifications.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable).map(this::response);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getInApp(String recipientId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notifications.findByRecipientIdAndChannelAndReadAtIsNullOrderByCreatedAtDesc(
                        recipientId, NotificationChannel.IN_APP, pageable)
                : notifications.findByRecipientIdAndChannelOrderByCreatedAtDesc(
                        recipientId, NotificationChannel.IN_APP, pageable);
        return page.map(this::response);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> search(NotificationEventType eventType, NotificationChannel channel,
                                              NotificationStatus status, String recipientId,
                                              RelatedEntityType relatedEntityType, String relatedEntityId,
                                              Instant from, Instant to, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new NotificationException(HttpStatus.BAD_REQUEST, "from must be before or equal to to");
        }
        Specification<Notification> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (eventType != null) predicates.add(builder.equal(root.get("eventType"), eventType));
            if (channel != null) predicates.add(builder.equal(root.get("channel"), channel));
            if (status != null) predicates.add(builder.equal(root.get("notificationStatus"), status));
            if (recipientId != null) predicates.add(builder.equal(root.get("recipientId"), recipientId));
            if (relatedEntityType != null) {
                predicates.add(builder.equal(root.get("relatedEntityType"), relatedEntityType));
            }
            if (relatedEntityId != null) {
                predicates.add(builder.equal(root.get("relatedEntityId"), relatedEntityId));
            }
            if (from != null) predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), to));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return notifications.findAll(specification, pageable).map(this::response);
    }

    @Transactional
    public NotificationResponse markRead(Long id, String principal, boolean privileged) {
        Notification notification = lock(id);
        if (notification.getChannel() != NotificationChannel.IN_APP) {
            throw new NotificationException(HttpStatus.CONFLICT, "Only in-app notifications can be marked read");
        }
        if (!privileged && !notification.getRecipientId().equals(principal)) {
            throw new NotificationException(HttpStatus.FORBIDDEN, "Notification does not belong to this recipient");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
        return response(notifications.save(notification));
    }

    @Transactional
    public NotificationPreferenceResponse savePreference(String recipientId, RecipientType recipientType,
                                                          NotificationChannel channel, boolean enabled) {
        NotificationPreference preference = preferences
                .findByRecipientIdAndRecipientTypeAndChannel(recipientId, recipientType, channel)
                .orElseGet(NotificationPreference::new);
        preference.setRecipientId(recipientId);
        preference.setRecipientType(recipientType);
        preference.setChannel(channel);
        preference.setEnabled(enabled);
        return preferenceResponse(preferences.save(preference));
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> getPreferences(String recipientId, RecipientType recipientType) {
        return preferences.findByRecipientIdAndRecipientTypeOrderByChannel(recipientId, recipientType)
                .stream().map(this::preferenceResponse).toList();
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(String recipientId, RecipientType recipientType, NotificationChannel channel) {
        return preferences.findByRecipientIdAndRecipientTypeAndChannel(recipientId, recipientType, channel)
                .map(NotificationPreference::isEnabled).orElse(true);
    }

    @Transactional(readOnly = true)
    public List<Long> dueIds(int batchSize) {
        return notifications.findDueIds(NotificationStatus.PENDING, NotificationStatus.FAILED,
                Instant.now(), PageRequest.of(0, batchSize));
    }

    @Transactional(readOnly = true)
    public List<Long> staleIds(Instant cutoff, int batchSize) {
        return notifications.findStaleIds(NotificationStatus.PROCESSING, cutoff,
                PageRequest.of(0, batchSize));
    }

    @Transactional
    public DeliveryWork claim(Long id) {
        Notification notification = lockOrNull(id).orElse(null);
        if (notification == null) return null;
        Instant now = Instant.now();
        boolean pendingDue = notification.getNotificationStatus() == NotificationStatus.PENDING
                && !notification.getScheduledAt().isAfter(now);
        boolean retryDue = notification.getNotificationStatus() == NotificationStatus.FAILED
                && notification.getNextAttemptAt() != null
                && !notification.getNextAttemptAt().isAfter(now)
                && notification.getAttemptCount() < notification.getMaxAttempts();
        if (!pendingDue && !retryDue) return null;
        notification.setNotificationStatus(NotificationStatus.PROCESSING);
        notification.setNextAttemptAt(null);
        notification.setAttemptCount(notification.getAttemptCount() + 1);
        NotificationAttempt attempt = new NotificationAttempt();
        attempt.setNotification(notification);
        attempt.setAttemptNumber(notification.getAttemptCount());
        attempt.setAttemptStatus(AttemptStatus.PROCESSING);
        attempt.setAttemptedAt(now);
        attempts.save(attempt);
        notifications.saveAndFlush(notification);
        return new DeliveryWork(notification, notification.getAttemptCount());
    }

    @Transactional
    public void complete(Long id, int attemptNumber, ProviderResult result, String failureReason,
                         long initialDelayMs) {
        Notification notification = lock(id);
        NotificationAttempt attempt = attempts.findByNotificationIdAndAttemptNumber(id, attemptNumber)
                .orElseThrow(() -> new NotificationException(HttpStatus.CONFLICT,
                        "Notification attempt history is missing"));
        if (notification.getNotificationStatus() != NotificationStatus.PROCESSING
                || attempt.getAttemptStatus() != AttemptStatus.PROCESSING) {
            return;
        }
        Instant now = Instant.now();
        attempt.setCompletedAt(now);
        if (failureReason == null) {
            attempt.setAttemptStatus(AttemptStatus.SENT);
            attempt.setProviderReference(result.providerReference());
            notification.setNotificationStatus(NotificationStatus.SENT);
            notification.setSentAt(now);
            notification.setFailureReason(null);
        } else {
            attempt.setAttemptStatus(AttemptStatus.FAILED);
            attempt.setFailureReason(failureReason);
            notification.setNotificationStatus(NotificationStatus.FAILED);
            notification.setFailureReason(failureReason);
            if (notification.getAttemptCount() < notification.getMaxAttempts()) {
                long multiplier = 1L << Math.min(notification.getAttemptCount() - 1, 10);
                notification.setNextAttemptAt(now.plusMillis(initialDelayMs * multiplier));
            }
        }
        attempts.save(attempt);
        notifications.save(notification);
    }

    @Transactional
    public void recoverStale(Long id, Instant cutoff) {
        Notification notification = lockOrNull(id).orElse(null);
        if (notification == null || notification.getNotificationStatus() != NotificationStatus.PROCESSING
                || notification.getUpdatedAt().isAfter(cutoff)) return;
        NotificationAttempt attempt = attempts.findTopByNotificationIdOrderByAttemptNumberDesc(id).orElse(null);
        String reason = "Delivery worker stopped while processing; provider outcome may be unknown";
        if (attempt != null && attempt.getAttemptStatus() == AttemptStatus.PROCESSING) {
            attempt.setAttemptStatus(AttemptStatus.FAILED);
            attempt.setFailureReason(reason);
            attempt.setCompletedAt(Instant.now());
            attempts.save(attempt);
        }
        notification.setNotificationStatus(NotificationStatus.FAILED);
        notification.setFailureReason(reason);
        if (notification.getAttemptCount() < notification.getMaxAttempts()) {
            notification.setNextAttemptAt(Instant.now());
        }
        notifications.save(notification);
    }

    @Transactional
    public NotificationResponse retry(Long id) {
        Notification notification = lock(id);
        if (notification.getNotificationStatus() == NotificationStatus.CANCELLED) {
            throw new NotificationException(HttpStatus.CONFLICT, "Cancelled notifications cannot be retried");
        }
        if (notification.getNotificationStatus() != NotificationStatus.FAILED) {
            throw new NotificationException(HttpStatus.CONFLICT, "Only failed notifications can be retried");
        }
        if (notification.getAttemptCount() >= notification.getMaxAttempts()) {
            throw new NotificationException(HttpStatus.CONFLICT, "Maximum notification attempts reached");
        }
        notification.setNextAttemptAt(Instant.now());
        return response(notifications.save(notification));
    }

    @Transactional(readOnly = true)
    public NotificationResponse existingForKey(String key, String fingerprint) {
        return notifications.findByIdempotencyKey(key).map(notification -> {
            if (!notification.getRequestFingerprint().equals(fingerprint)) {
                throw new NotificationException(HttpStatus.CONFLICT,
                        "Idempotency-Key was already used with a different request");
            }
            return response(notification);
        }).orElse(null);
    }

    @Transactional(readOnly = true)
    public NotificationResponse existingForBusinessDedup(NotificationEventType event, String recipientId,
                                                           NotificationChannel channel, String reference) {
        if (reference == null) return null;
        return notifications.findByEventTypeAndRecipientIdAndChannelAndBusinessDeduplicationReference(
                event, recipientId, channel, reference).map(this::response).orElse(null);
    }

    private Notification lock(Long id) {
        return lockOrNull(id).orElseThrow(
                () -> new NotificationException(HttpStatus.NOT_FOUND, "Notification was not found"));
    }

    private Optional<Notification> lockOrNull(Long id) {
        return notifications.findByIdForUpdate(id);
    }

    private NotificationResponse response(Notification notification) {
        List<NotificationAttemptResponse> history = attempts
                .findByNotificationIdOrderByAttemptNumberAsc(notification.getId()).stream()
                .map(attempt -> new NotificationAttemptResponse(attempt.getAttemptNumber(),
                        attempt.getAttemptStatus(), attempt.getProviderReference(), attempt.getFailureReason(),
                        attempt.getAttemptedAt(), attempt.getCompletedAt())).toList();
        return new NotificationResponse(notification.getId(), notification.getNotificationReference(),
                notification.getEventType(), notification.getRecipientId(), notification.getRecipientType(),
                notification.getRecipientAddress(), notification.getChannel(), notification.getSubject(),
                notification.getMessage(), notification.getRelatedEntityType(), notification.getRelatedEntityId(),
                notification.getNotificationStatus(), notification.getScheduledAt(), notification.getSentAt(),
                notification.getFailureReason(), notification.getTemplateCode(), notification.getTemplateId(),
                notification.getReadAt(), notification.getAttemptCount(), notification.getMaxAttempts(),
                notification.getCreatedAt(), notification.getUpdatedAt(), history);
    }

    private NotificationPreferenceResponse preferenceResponse(NotificationPreference preference) {
        return new NotificationPreferenceResponse(preference.getRecipientId(), preference.getRecipientType(),
                preference.getChannel(), preference.isEnabled(), preference.getCreatedAt(), preference.getUpdatedAt());
    }

    public record DeliveryWork(Notification notification, int attemptNumber) {
    }
}
