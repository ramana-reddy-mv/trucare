package com.trucare.notification.service;

import com.trucare.notification.exception.NotificationException;
import com.trucare.notification.model.NotificationChannel;
import com.trucare.notification.provider.NotificationProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class NotificationProviderRegistry {
    private final Map<NotificationChannel, NotificationProvider> providers =
            new EnumMap<>(NotificationChannel.class);

    public NotificationProviderRegistry(List<NotificationProvider> configuredProviders) {
        for (NotificationProvider provider : configuredProviders) {
            NotificationProvider previous = providers.put(provider.channel(), provider);
            if (previous != null) {
                throw new IllegalStateException("Multiple notification providers configured for " + provider.channel());
            }
        }
    }

    public NotificationProvider get(NotificationChannel channel) {
        NotificationProvider provider = providers.get(channel);
        if (provider == null) {
            throw new NotificationException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No provider is configured for notification channel " + channel);
        }
        return provider;
    }
}
