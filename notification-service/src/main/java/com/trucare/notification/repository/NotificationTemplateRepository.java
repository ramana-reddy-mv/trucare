package com.trucare.notification.repository;

import com.trucare.notification.model.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {
    Optional<NotificationTemplate> findByTemplateCode(String templateCode);
    Optional<NotificationTemplate> findByEventTypeAndChannelAndActiveTrue(
            NotificationEventType eventType, NotificationChannel channel);
    Page<NotificationTemplate> findAllByOrderByUpdatedAtDesc(Pageable pageable);
}
