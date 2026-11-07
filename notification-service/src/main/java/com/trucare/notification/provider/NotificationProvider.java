package com.trucare.notification.provider;

import com.trucare.notification.model.Notification;
import com.trucare.notification.model.NotificationChannel;

public interface NotificationProvider {
    NotificationChannel channel();
    ProviderResult send(Notification notification);

    default ProviderResult send(Notification notification, String providerIdempotencyKey) {
        return send(notification);
    }
}
