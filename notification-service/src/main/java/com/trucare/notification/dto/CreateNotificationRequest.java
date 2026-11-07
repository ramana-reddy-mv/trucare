package com.trucare.notification.dto;

import com.trucare.notification.model.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

public record CreateNotificationRequest(
        @NotNull NotificationEventType eventType,
        @NotBlank @Size(max = 100) String recipientId,
        @NotNull RecipientType recipientType,
        @NotNull NotificationChannel channel,
        @Size(max = 320) String recipientAddress,
        @NotNull RelatedEntityType relatedEntityType,
        @NotBlank @Size(max = 100) String relatedEntityId,
        @Size(max = 100) String templateCode,
        @Size(max = 128) String businessDeduplicationReference,
        Instant scheduledAt,
        Map<@NotBlank @Size(max = 80) String, @Size(max = 2000) String> variables) {
}
