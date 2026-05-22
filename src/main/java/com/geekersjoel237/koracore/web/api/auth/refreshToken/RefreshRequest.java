package com.geekersjoel237.koracore.web.api.auth.refreshToken;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {}