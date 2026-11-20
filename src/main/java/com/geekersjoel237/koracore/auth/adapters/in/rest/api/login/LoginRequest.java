package com.geekersjoel237.koracore.auth.adapters.in.rest.api.login;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "PIN is required")
        String rawPin
) {
}