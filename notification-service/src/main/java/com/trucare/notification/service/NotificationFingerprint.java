package com.trucare.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trucare.notification.dto.CreateNotificationRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.TreeMap;

@Component
public class NotificationFingerprint {
    private final ObjectMapper objectMapper;

    public NotificationFingerprint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String calculate(CreateNotificationRequest request) {
        LinkedHashMap<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("eventType", request.eventType());
        canonical.put("recipientId", request.recipientId());
        canonical.put("recipientType", request.recipientType());
        canonical.put("channel", request.channel());
        canonical.put("recipientAddress", request.recipientAddress());
        canonical.put("relatedEntityType", request.relatedEntityType());
        canonical.put("relatedEntityId", request.relatedEntityId());
        canonical.put("templateCode", request.templateCode());
        canonical.put("businessDeduplicationReference", request.businessDeduplicationReference());
        canonical.put("scheduledAt", request.scheduledAt());
        canonical.put("variables", request.variables() == null ? null : new TreeMap<>(request.variables()));
        try {
            byte[] data = objectMapper.writeValueAsBytes(canonical);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not fingerprint notification request", exception);
        }
    }
}
