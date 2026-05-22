package com.geekersjoel237.koracore.web.api.auth.login;

import com.geekersjoel237.koracore.application.command.LoginCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "PIN is required")
        String rawPin
) {
    public LoginCommand toCommand() {
        return new LoginCommand(email, rawPin);
    }
}