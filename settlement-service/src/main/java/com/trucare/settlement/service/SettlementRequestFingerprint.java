package com.trucare.settlement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trucare.settlement.dto.CreateSettlementRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

@Component
public class SettlementRequestFingerprint {

    private final ObjectMapper objectMapper;

    public SettlementRequestFingerprint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String calculate(CreateSettlementRequest request) {
        var payload = Arrays.asList(
                trim(request.getClaimId()),
                request.getPolicyId(),
                request.getSettlementType(),
                request.getDeductibleAmount(),
                request.getCopayAmount(),
                request.getNonPayableAmount(),
                request.getApprovedClaimAmount(),
                trimNullable(request.getMemberId()),
                trimNullable(request.getProviderId()));
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(payload);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not fingerprint settlement request", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String trimNullable(String value) {
        return value == null ? null : value.trim();
    }
}
