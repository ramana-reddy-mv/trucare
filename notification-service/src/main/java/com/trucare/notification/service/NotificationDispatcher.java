package com.trucare.notification.service;

import com.trucare.notification.model.Notification;
import com.trucare.notification.provider.ProviderResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationDispatcher {
    private final NotificationStore store;
    private final NotificationProviderRegistry providers;
    private final long retryInitialDelayMs;

    public NotificationDispatcher(NotificationStore store, NotificationProviderRegistry providers,
                                  @Value("${notification.delivery.initial-delay-ms:1000}")
                                  long retryInitialDelayMs) {
        this.store = store;
        this.providers = providers;
        if (retryInitialDelayMs < 1) {
            throw new IllegalStateException("Notification retry initial delay must be positive");
        }
        this.retryInitialDelayMs = retryInitialDelayMs;
    }

    public boolean dispatch(Long notificationId) {
        NotificationStore.DeliveryWork work = store.claim(notificationId);
        if (work == null) return false;
        Notification notification = work.notification();
        ProviderResult result = null;
        String failure = null;
        try {
            result = providers.get(notification.getChannel()).send(notification,
                    notification.getNotificationReference());
            if (result == null || result.providerReference() == null
                    || result.providerReference().isBlank()) {
                failure = "Notification provider returned no delivery reference";
            }
        } catch (RuntimeException exception) {
            failure = "Notification provider failed (" + exception.getClass().getSimpleName() + ")";
            log.warn("Notification delivery failed for reference={} channel={} errorType={}",
                    notification.getNotificationReference(), notification.getChannel(),
                    exception.getClass().getSimpleName());
        }
        store.complete(notification.getId(), work.attemptNumber(), result, failure, retryInitialDelayMs);
        return true;
    }
}
