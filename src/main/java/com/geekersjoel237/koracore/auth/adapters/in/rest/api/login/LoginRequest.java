package com.geekersjoel237.koracore.auth.adapters.in.rest.api.login;

import com.geekersjoel237.koracore.auth.application.command.LoginCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "PIN is required")
        String rawPin
) {
    public LoginCommand toCommand(Id correlationId) {
        return new LoginCommand(correlationId, email, Pin.of(rawPin));
    }
}