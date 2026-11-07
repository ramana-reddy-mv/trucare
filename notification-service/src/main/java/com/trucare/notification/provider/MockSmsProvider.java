package com.trucare.notification.provider;

import com.trucare.notification.model.Notification;
import org.springframework.stereotype.Component;

@Component
public class MockSmsProvider implements SmsNotificationProvider {
    @Override
    public ProviderResult send(Notification notification) {
        return send(notification, notification.getNotificationReference());
    }

    @Override
    public ProviderResult send(Notification notification, String providerIdempotencyKey) {
        return new ProviderResult("MOCK-SMS-" + providerIdempotencyKey);
    }
}
