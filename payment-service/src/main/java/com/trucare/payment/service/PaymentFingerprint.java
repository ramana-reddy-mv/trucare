package com.trucare.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trucare.payment.dto.InitiatePaymentRequest;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class PaymentFingerprint {
    private final ObjectMapper objectMapper;

    public PaymentFingerprint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String calculate(InitiatePaymentRequest request) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(new FingerprintPayload(
                            request.getSettlementId(), request.getPaymentMethod().trim().toUpperCase())));
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not fingerprint payment request", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private record FingerprintPayload(Long settlementId, String paymentMethod) {
    }
}
