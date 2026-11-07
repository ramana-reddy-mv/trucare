package com.trucare.notification.provider;

import com.trucare.notification.model.NotificationChannel;

public interface SmsNotificationProvider extends NotificationProvider {
    @Override
    default NotificationChannel channel() {
        return NotificationChannel.SMS;
    }
}
