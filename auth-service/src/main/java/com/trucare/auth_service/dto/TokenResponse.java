package com.trucare.auth_service.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
