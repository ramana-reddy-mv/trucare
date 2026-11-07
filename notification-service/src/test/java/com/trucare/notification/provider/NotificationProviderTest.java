package com.trucare.notification.provider;

import com.trucare.notification.model.Notification;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationProviderTest {
    @Test
    void mockEmailAndSmsProvidersReturnDeterministicReferences() {
        Notification notification = new Notification();
        MockEmailNotificationProvider email = new MockEmailNotificationProvider();
        MockSmsProvider sms = new MockSmsProvider();

        assertEquals(email.send(notification, "NTF-1").providerReference(),
                email.send(notification, "NTF-1").providerReference());
        assertEquals("MOCK-SMS-NTF-1", sms.send(notification, "NTF-1").providerReference());
    }

    @Test
    void emailProviderSendsConfiguredMessage() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailNotificationProvider provider = new EmailNotificationProvider(sender, "noreply@example.com", "smtp.example.com");
        Notification notification = new Notification();
        notification.setRecipientAddress("member@example.com");
        notification.setSubject("Subject");
        notification.setMessage("Body");

        ProviderResult result = provider.send(notification, "NTF-1");

        assertTrue(result.providerReference().startsWith("SMTP-"));
        verify(sender).send(any(SimpleMailMessage.class));
    }

    @Test
    void emailProviderFailurePropagatesToRetryDispatcher() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("provider error")).when(sender).send(any(SimpleMailMessage.class));
        EmailNotificationProvider provider = new EmailNotificationProvider(sender, "noreply@example.com", "smtp.example.com");
        Notification notification = new Notification();
        notification.setRecipientAddress("member@example.com");
        notification.setSubject("Subject");
        notification.setMessage("Body");

        assertThrows(MailSendException.class, () -> provider.send(notification, "NTF-1"));
    }

    @Test
    void inAppProviderReturnsReferenceForPersistedNotification() {
        Notification notification = new Notification();
        notification.setNotificationReference("NTF-1");

        assertEquals("IN-APP-NTF-1",
                new InAppNotificationProvider().send(notification, "NTF-1").providerReference());
    }
}
