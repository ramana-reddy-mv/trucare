package com.trucare.notification.dto;

import com.trucare.notification.model.*;

import java.time.Instant;

public record NotificationTemplateResponse(String templateCode, NotificationEventType eventType,
                                           NotificationChannel channel, String subjectTemplate,
                                           String messageTemplate, boolean active, long version,
                                           Instant createdAt, Instant updatedAt) {
}
