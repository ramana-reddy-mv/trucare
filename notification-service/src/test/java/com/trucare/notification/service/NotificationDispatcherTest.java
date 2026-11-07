package com.trucare.notification.service;

import com.trucare.notification.model.*;
import com.trucare.notification.provider.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {
    @Mock private NotificationStore store;
    @Mock private NotificationProviderRegistry providers;
    @Mock private NotificationProvider provider;

    private NotificationDispatcher dispatcher;
    private Notification notification;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(store, providers, 1000);
        notification = new Notification();
        notification.setId(5L);
        notification.setNotificationReference("NTF-5");
        notification.setChannel(NotificationChannel.EMAIL);
    }

    @Test
    void recordsProviderSuccessWithoutHoldingDeliveryInApi() {
        when(store.claim(5L)).thenReturn(new NotificationStore.DeliveryWork(notification, 1));
        when(providers.get(NotificationChannel.EMAIL)).thenReturn(provider);
        when(provider.send(notification, "NTF-5")).thenReturn(new ProviderResult("PROVIDER-REF"));

        dispatcher.dispatch(5L);

        verify(store).complete(5L, 1, new ProviderResult("PROVIDER-REF"), null, 1000);
    }

    @Test
    void providerFailureIsPersistedAsAFailedAttemptForBoundedRetry() {
        when(store.claim(5L)).thenReturn(new NotificationStore.DeliveryWork(notification, 2));
        when(providers.get(NotificationChannel.EMAIL)).thenReturn(provider);
        when(provider.send(notification, "NTF-5")).thenThrow(new MailSendException("sensitive content"));

        dispatcher.dispatch(5L);

        verify(store).complete(5L, 2, null, "Notification provider failed (MailSendException)", 1000);
    }

    @Test
    void doesNotCallProviderWhenAnotherWorkerAlreadyClaimedNotification() {
        when(store.claim(5L)).thenReturn(null);

        dispatcher.dispatch(5L);

        verifyNoInteractions(providers, provider);
    }
}
