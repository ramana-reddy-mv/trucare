package com.trucare.settlement.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "settlements", indexes = {
        @Index(name = "idx_settlement_member_created", columnList = "member_id,created_at"),
        @Index(name = "idx_settlement_provider_created", columnList = "provider_id,created_at"),
        @Index(name = "idx_settlement_status_created", columnList = "settlement_status,created_at")
})
@Check(name = "ck_settlement_amounts",
        constraints = "approved_claim_amount >= 0 AND deductible_amount >= 0 AND copay_amount >= 0 "
                + "AND non_payable_amount >= 0 AND payable_amount >= 0 "
                + "AND deductible_amount + copay_amount + non_payable_amount <= approved_claim_amount")
@Getter
@Setter
@NoArgsConstructor
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "settlement_number", nullable = false, unique = true, length = 40)
    private String settlementNumber;

    @NotBlank
    @Column(name = "claim_id", nullable = false, unique = true, length = 64)
    private String claimId;

    @NotBlank
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @NotBlank
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @NotBlank
    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @NotNull
    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_type", nullable = false, length = 20)
    private SettlementType settlementType;

    @NotNull
    @DecimalMin(value = "0.0")
    @Column(name = "approved_claim_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal approvedClaimAmount;

    @NotNull
    @DecimalMin(value = "0.0")
    @Column(name = "deductible_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal deductibleAmount;

    @NotNull
    @DecimalMin(value = "0.0")
    @Column(name = "copay_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal copayAmount;

    @NotNull
    @DecimalMin(value = "0.0")
    @Column(name = "non_payable_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal nonPayableAmount;

    @NotNull
    @DecimalMin(value = "0.0")
    @Column(name = "payable_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal payableAmount;

    @NotBlank
    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false, length = 24)
    private SettlementStatus settlementStatus;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 16)
    private RecipientType recipientType;

    @NotBlank
    @Column(name = "recipient_id", nullable = false, length = 64)
    private String recipientId;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "settlement", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<SettlementBreakdown> breakdown = new ArrayList<>();

    @OneToMany(mappedBy = "settlement", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("createdAt ASC, id ASC")
    private List<SettlementAudit> auditHistory = new ArrayList<>();

    @OneToMany(mappedBy = "settlement", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("createdAt ASC, id ASC")
    private List<SettlementPaymentTransaction> paymentTransactions = new ArrayList<>();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (payableAmount == null) {
            payableAmount = BigDecimal.ZERO.setScale(2);
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
