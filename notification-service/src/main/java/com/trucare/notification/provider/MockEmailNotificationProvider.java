package com.trucare.notification.provider;

import com.trucare.notification.model.Notification;
import com.trucare.notification.model.NotificationChannel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notification.email.mode", havingValue = "MOCK", matchIfMissing = true)
public class MockEmailNotificationProvider implements NotificationProvider {
    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public ProviderResult send(Notification notification) {
        return send(notification, notification.getNotificationReference());
    }

    @Override
    public ProviderResult send(Notification notification, String providerIdempotencyKey) {
        return new ProviderResult("MOCK-EMAIL-" + providerIdempotencyKey);
    }
}
