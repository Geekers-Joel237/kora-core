package com.geekersjoel237.koracore.auth.adapters.in.rest.api.refreshToken;

import com.geekersjoel237.koracore.auth.application.command.RefreshTokensCommand;
import com.geekersjoel237.koracore.auth.domain.vo.RefreshToken;

import jakarta.validation.constraints.NotBlank;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

public record RefreshRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {

    public RefreshTokensCommand toCommand(Id correlationId) {
        return new RefreshTokensCommand(correlationId, RefreshToken.of(refreshToken));
    }
}