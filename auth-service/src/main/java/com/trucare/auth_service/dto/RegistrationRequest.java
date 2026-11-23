package com.trucare.auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank(message = "is required")
        @Size(min = 3, max = 100, message = "must be between 3 and 100 characters")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "may contain only letters, numbers, '.', '_' and '-'")
        String name,
        @NotBlank(message = "is required")
        @Email(message = "must be a valid email address")
        @Size(max = 254, message = "must be at most 254 characters")
        String email,
        @NotBlank(message = "is required")
        @Size(min = 12, max = 72, message = "must be between 12 and 72 characters")
        String password) {
}
