package com.trucare.notification.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "notification_templates", uniqueConstraints = @UniqueConstraint(
        name = "uk_template_active_event_channel", columnNames = "active_template_key"),
        indexes = @Index(name = "idx_template_active", columnList = "active,event_type,channel"))
@Getter
@Setter
@NoArgsConstructor
public class NotificationTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_code", nullable = false, unique = true, length = 100)
    private String templateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private NotificationEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private NotificationChannel channel;

    @Column(name = "subject_template", length = 200)
    private String subjectTemplate;

    @Column(name = "message_template", nullable = false, length = 5000)
    private String messageTemplate;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "active_template_key", length = 64)
    private String activeTemplateKey;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
