package com.trucare.notification.service;

import com.trucare.notification.dto.CreateNotificationRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class NotificationEventHandler {
    private final NotificationService notificationService;

    public NotificationEventHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public NotificationService.CreationResult handle(@Valid CreateNotificationRequest event, String idempotencyKey) {
        return notificationService.create(event, idempotencyKey);
    }
}
