package com.trucare.notification.dto;

import com.trucare.notification.model.AttemptStatus;

import java.time.Instant;

public record NotificationAttemptResponse(int attemptNumber, AttemptStatus attemptStatus,
                                         String providerReference, String failureReason,
                                         Instant attemptedAt, Instant completedAt) {
}
