package com.trucare.notification.dto;

import com.trucare.notification.model.*;

import java.time.Instant;

public record NotificationPreferenceResponse(String recipientId, RecipientType recipientType,
                                            NotificationChannel channel, boolean enabled,
                                            Instant createdAt, Instant updatedAt) {
}
