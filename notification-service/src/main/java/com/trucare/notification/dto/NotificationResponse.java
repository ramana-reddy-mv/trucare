package com.trucare.notification.dto;

import com.trucare.notification.model.*;

import java.time.Instant;
import java.util.List;

public record NotificationResponse(
        Long id,
        String notificationReference,
        NotificationEventType eventType,
        String recipientId,
        RecipientType recipientType,
        String recipientAddress,
        NotificationChannel channel,
        String subject,
        String message,
        RelatedEntityType relatedEntityType,
        String relatedEntityId,
        NotificationStatus notificationStatus,
        Instant scheduledAt,
        Instant sentAt,
        String failureReason,
        String templateCode,
        Long templateId,
        Instant readAt,
        int attemptCount,
        int maxAttempts,
        Instant createdAt,
        Instant updatedAt,
        List<NotificationAttemptResponse> attempts) {
}
