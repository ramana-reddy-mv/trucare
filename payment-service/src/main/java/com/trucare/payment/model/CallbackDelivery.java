package com.trucare.payment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "settlement_callback_deliveries", indexes = {
        @Index(name = "idx_callback_due", columnList = "delivery_status,next_attempt_at"),
        @Index(name = "idx_callback_payment", columnList = "payment_id")
})
@Getter
@Setter
@NoArgsConstructor
public class CallbackDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_status", nullable = false, length = 24)
    private CallbackTargetStatus targetStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 16)
    private CallbackStatus deliveryStatus;

    @Column(length = 1000)
    private String remarks;

    @Column(name = "start_gateway_after_delivery", nullable = false)
    private boolean startGatewayAfterDelivery;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "delivery_started_at")
    private Instant deliveryStartedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
