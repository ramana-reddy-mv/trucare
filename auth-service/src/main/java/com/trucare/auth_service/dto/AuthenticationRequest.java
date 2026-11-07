package com.trucare.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthenticationRequest(
        @NotBlank @Size(max = 254) String usernameOrEmail,
        @NotBlank @Size(min = 1, max = 72) String password) {
}
