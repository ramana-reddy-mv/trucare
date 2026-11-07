package com.trucare.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InitiatePaymentRequest {
    @NotNull
    @Positive
    private Long settlementId;

    @NotBlank
    @Size(max = 32)
    private String paymentMethod;
}
