package com.trucare.notification.repository;

import com.trucare.notification.model.NotificationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationAttemptRepository extends JpaRepository<NotificationAttempt, Long> {
    List<NotificationAttempt> findByNotificationIdOrderByAttemptNumberAsc(Long notificationId);
    Optional<NotificationAttempt> findTopByNotificationIdOrderByAttemptNumberDesc(Long notificationId);
    Optional<NotificationAttempt> findByNotificationIdAndAttemptNumber(Long notificationId, int attemptNumber);
}
