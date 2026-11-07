package com.trucare.notification.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_due", columnList = "notification_status,next_attempt_at"),
        @Index(name = "idx_notification_recipient", columnList = "recipient_id,created_at"),
        @Index(name = "idx_notification_related", columnList = "related_entity_type,related_entity_id"),
        @Index(name = "idx_notification_event", columnList = "event_type,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_idempotency", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "uk_notification_business_dedup",
                columnNames = {"event_type", "recipient_id", "channel", "business_deduplication_reference"})
})
@Getter
@Setter
@NoArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "notification_reference", nullable = false, unique = true, length = 48)
    private String notificationReference;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private NotificationEventType eventType;

    @NotBlank
    @Column(name = "recipient_id", nullable = false, length = 100)
    private String recipientId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 16)
    private RecipientType recipientType;

    @Column(name = "recipient_address", length = 320)
    private String recipientAddress;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private NotificationChannel channel;

    @Column(length = 200)
    private String subject;

    @NotBlank
    @Column(nullable = false, length = 5000)
    private String message;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "related_entity_type", nullable = false, length = 16)
    private RelatedEntityType relatedEntityType;

    @NotBlank
    @Column(name = "related_entity_id", nullable = false, length = 100)
    private String relatedEntityId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_status", nullable = false, length = 16)
    private NotificationStatus notificationStatus;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @NotBlank
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @NotBlank
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "business_deduplication_reference", length = 128)
    private String businessDeduplicationReference;

    @Column(name = "template_code", nullable = false, length = 100)
    private String templateCode;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "notification")
    @OrderBy("attemptNumber ASC")
    private List<NotificationAttempt> attempts = new ArrayList<>();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (scheduledAt == null) scheduledAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
