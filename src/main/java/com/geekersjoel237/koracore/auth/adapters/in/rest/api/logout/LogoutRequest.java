package com.geekersjoel237.koracore.auth.adapters.in.rest.api.logout;

import com.geekersjoel237.koracore.auth.application.command.LogoutCommand;
import com.geekersjoel237.koracore.auth.domain.vo.RefreshToken;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {

    public LogoutCommand toCommand(Id correlationId) {
        return new LogoutCommand(correlationId, RefreshToken.of(refreshToken));
    }
}
