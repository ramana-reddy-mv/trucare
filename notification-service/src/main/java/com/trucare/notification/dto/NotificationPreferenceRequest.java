package com.trucare.notification.dto;

import com.trucare.notification.model.NotificationChannel;
import jakarta.validation.constraints.NotNull;

public record NotificationPreferenceRequest(@NotNull NotificationChannel channel, boolean enabled) {
}
