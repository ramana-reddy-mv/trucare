package com.trucare.notification.provider;

import com.trucare.notification.model.Notification;
import com.trucare.notification.model.NotificationChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "notification.email.mode", havingValue = "SMTP")
public class EmailNotificationProvider implements NotificationProvider {
    private final JavaMailSender mailSender;
    private final String from;

    public EmailNotificationProvider(JavaMailSender mailSender,
                                     @Value("${notification.email.from}") String from,
                                     @Value("${spring.mail.host:}") String host) {
        if (from.isBlank() || host.isBlank()) {
            throw new IllegalStateException("SMTP_FROM and SMTP_HOST are required when SMTP email mode is enabled");
        }
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public ProviderResult send(Notification notification) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(notification.getRecipientAddress());
        mail.setSubject(notification.getSubject());
        mail.setText(notification.getMessage());
        mailSender.send(mail);
        return new ProviderResult("SMTP-" + UUID.randomUUID());
    }
}
