package com.trucare.payment.dto;

import com.trucare.payment.model.PaymentStatus;
import lombok.Data;

import java.time.Instant;

@Data
public class PaymentAuditResponse {
    private Long id;
    private PaymentStatus previousStatus;
    private PaymentStatus newStatus;
    private String action;
    private String remarks;
    private Instant createdAt;
}
