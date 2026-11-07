package com.trucare.settlement.dto;

import com.trucare.settlement.model.SettlementStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PaymentStatusRequest {

    @NotNull
    private SettlementStatus status;

    @Size(max = 128)
    private String paymentReference;

    @Size(max = 1000)
    private String remarks;
}
