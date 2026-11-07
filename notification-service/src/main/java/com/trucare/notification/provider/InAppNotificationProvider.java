package com.trucare.notification.provider;

import com.trucare.notification.model.Notification;
import com.trucare.notification.model.NotificationChannel;
import org.springframework.stereotype.Component;

@Component
public class InAppNotificationProvider implements NotificationProvider {
    @Override
    public NotificationChannel channel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public ProviderResult send(Notification notification) {
        return new ProviderResult("IN-APP-" + notification.getNotificationReference());
    }
}
