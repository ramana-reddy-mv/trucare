package com.trucare.notification.repository;

import com.trucare.notification.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long>,
        JpaSpecificationExecutor<Notification> {
    Optional<Notification> findByNotificationReference(String reference);
    Optional<Notification> findByIdempotencyKey(String key);
    Optional<Notification> findByEventTypeAndRecipientIdAndChannelAndBusinessDeduplicationReference(
            NotificationEventType eventType, String recipientId, NotificationChannel channel, String reference);
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId, Pageable pageable);
    Page<Notification> findByRecipientIdAndChannelOrderByCreatedAtDesc(
            String recipientId, NotificationChannel channel, Pageable pageable);
    Page<Notification> findByRecipientIdAndChannelAndReadAtIsNullOrderByCreatedAtDesc(
            String recipientId, NotificationChannel channel, Pageable pageable);

    @Query("select n.id from Notification n where "
            + "(n.notificationStatus = :pending and n.scheduledAt <= :now) or "
            + "(n.notificationStatus = :failed and n.nextAttemptAt <= :now and n.attemptCount < n.maxAttempts) "
            + "order by coalesce(n.nextAttemptAt, n.scheduledAt), n.id")
    List<Long> findDueIds(@Param("pending") NotificationStatus pending,
                          @Param("failed") NotificationStatus failed,
                          @Param("now") Instant now, Pageable pageable);

    @Query("select n.id from Notification n where n.notificationStatus = :status "
            + "and n.updatedAt <= :cutoff order by n.updatedAt, n.id")
    List<Long> findStaleIds(@Param("status") NotificationStatus status,
                            @Param("cutoff") Instant cutoff, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notification n where n.id = :id")
    Optional<Notification> findByIdForUpdate(@Param("id") Long id);
}
