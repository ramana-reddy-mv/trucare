package com.trucare.notification.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "notification_attempts", indexes = @Index(name = "idx_attempt_notification", columnList = "notification_id,attempt_number"),
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_attempt",
                columnNames = {"notification_id", "attempt_number"}))
@Getter
@Setter
@NoArgsConstructor
public class NotificationAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "attempt_status", nullable = false, length = 16)
    private AttemptStatus attemptStatus;

    @Column(name = "provider_reference", length = 200)
    private String providerReference;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
