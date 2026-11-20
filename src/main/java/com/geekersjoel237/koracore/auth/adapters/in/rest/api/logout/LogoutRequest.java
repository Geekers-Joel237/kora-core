package com.geekersjoel237.koracore.auth.adapters.in.rest.api.logout;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {
}
