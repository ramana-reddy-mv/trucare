package com.trucare.notification.repository;

import com.trucare.notification.model.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    Optional<NotificationPreference> findByRecipientIdAndRecipientTypeAndChannel(
            String recipientId, RecipientType recipientType, NotificationChannel channel);
    List<NotificationPreference> findByRecipientIdAndRecipientTypeOrderByChannel(
            String recipientId, RecipientType recipientType);
}
