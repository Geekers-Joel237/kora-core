package com.geekersjoel237.koracore.web.api.auth;

import com.geekersjoel237.koracore.application.command.LoginCommand;

public record LoginRequest(String email, String rawPin) {
    public LoginCommand toCommand() {
        return new LoginCommand(email, rawPin);
    }
}