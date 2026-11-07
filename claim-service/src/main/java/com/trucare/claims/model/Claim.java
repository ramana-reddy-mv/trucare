package com.trucare.claims.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "claims")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // Primary key for the claim

    @Column(unique = true)
    private String claimId;  // Unique claim number

    @Column(name = "idempotency_key", unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    private String memberId;  // ID of the member

    private String providerId;  // ID of the provider

    private BigDecimal claimAmount;  // Claim amount

    private String claimStatus;  // CLAIMED, APPROVED, REJECTED

    private LocalDateTime createdAt;  // Claim creation date

    private LocalDateTime updatedAt;  // Last updated timestamp

    private List<Long> documentIds;
}
