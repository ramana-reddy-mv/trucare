package com.trucare.notification.dto;

import com.trucare.notification.model.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NotificationTemplateRequest(
        @NotBlank @Size(max = 100) String templateCode,
        @NotNull NotificationEventType eventType,
        @NotNull NotificationChannel channel,
        @Size(max = 200) String subjectTemplate,
        @NotBlank @Size(max = 5000) String messageTemplate,
        boolean active) {
}
