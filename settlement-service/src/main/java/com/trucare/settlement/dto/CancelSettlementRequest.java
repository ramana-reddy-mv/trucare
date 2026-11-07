package com.trucare.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CancelSettlementRequest {

    @NotBlank
    @Size(max = 1000)
    private String reason;
}
