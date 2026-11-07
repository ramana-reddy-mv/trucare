package com.trucare.payment.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payment_settlement", columnList = "settlement_id"),
        @Index(name = "idx_payment_claim_status", columnList = "claim_id,payment_status"),
        @Index(name = "idx_payment_member_created", columnList = "member_id,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_settlement", columnNames = "settlement_id"),
        @UniqueConstraint(name = "uk_payment_idempotency", columnNames = "idempotency_key")
})
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "payment_reference", nullable = false, unique = true, length = 48)
    private String paymentReference;

    @NotNull
    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;

    @NotBlank
    @Column(name = "settlement_number", nullable = false, length = 48)
    private String settlementNumber;

    @NotBlank
    @Column(name = "claim_id", nullable = false, length = 64)
    private String claimId;

    @NotBlank
    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;

    @NotBlank
    @Column(name = "recipient_id", nullable = false, length = 64)
    private String recipientId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 16)
    private RecipientType recipientType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_type", nullable = false, length = 20)
    private SettlementType settlementType;

    @NotNull
    @DecimalMin("0.01")
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @NotBlank
    @Column(nullable = false, length = 3)
    private String currency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 16)
    private PaymentStatus paymentStatus;

    @NotBlank
    @Column(name = "payment_method", nullable = false, length = 32)
    private String paymentMethod;

    @NotBlank
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @NotBlank
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "provider_transaction_reference", unique = true, length = 128)
    private String providerTransactionReference;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "initiated_at", nullable = false)
    private Instant initiatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL)
    @OrderBy("attemptNumber ASC")
    private List<PaymentTransaction> transactions = new ArrayList<>();

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL)
    @OrderBy("createdAt ASC, id ASC")
    private List<PaymentAudit> auditHistory = new ArrayList<>();

    @OneToMany(mappedBy = "payment")
    private List<CallbackDelivery> callbackDeliveries = new ArrayList<>();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (initiatedAt == null) {
            initiatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
