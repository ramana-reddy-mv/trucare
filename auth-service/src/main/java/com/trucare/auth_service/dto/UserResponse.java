package com.trucare.auth_service.dto;

import java.util.List;

public record UserResponse(String username, String email, List<String> roles) {
}
