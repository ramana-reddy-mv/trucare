package com.trucare.payment.dto;

import com.trucare.payment.model.CallbackStatus;
import com.trucare.payment.model.CallbackTargetStatus;
import lombok.Data;

import java.time.Instant;

@Data
public class CallbackDeliveryResponse {
    private Long id;
    private CallbackTargetStatus targetStatus;
    private CallbackStatus deliveryStatus;
    private int attemptCount;
    private int maxAttempts;
    private Instant nextAttemptAt;
    private Instant createdAt;
    private Instant deliveredAt;
}
