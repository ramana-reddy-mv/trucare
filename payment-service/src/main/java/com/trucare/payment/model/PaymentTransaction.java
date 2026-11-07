package com.trucare.payment.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_transactions", indexes = @Index(
        name = "idx_payment_attempts", columnList = "payment_id,attempt_number"),
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_attempt", columnNames = {"payment_id", "attempt_number"}),
                @UniqueConstraint(name = "uk_payment_tx_reference", columnNames = "transaction_reference"),
                @UniqueConstraint(name = "uk_payment_provider_reference", columnNames = "provider_reference")
        })
@Getter
@Setter
@NoArgsConstructor
public class PaymentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "transaction_reference", nullable = false, length = 80)
    private String transactionReference;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @NotNull
    @DecimalMin("0.01")
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_status", nullable = false, length = 16)
    private PaymentTransactionStatus transactionStatus;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "request_timestamp", nullable = false)
    private Instant requestTimestamp;

    @Column(name = "response_timestamp")
    private Instant responseTimestamp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        if (requestTimestamp == null) {
            requestTimestamp = now;
        }
    }
}
