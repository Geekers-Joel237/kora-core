package com.geekersjoel237.koracore.auth.adapters.in.rest.api.refreshToken;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {
}