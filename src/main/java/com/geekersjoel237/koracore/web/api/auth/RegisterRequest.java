package com.geekersjoel237.koracore.web.api.auth;

import com.geekersjoel237.koracore.application.command.RegisterCommand;

public record RegisterRequest(
        String fullName,
        String email,
        String phonePrefix,
        String phoneNumber,
        String rawPin
) {
    public RegisterCommand toCommand() {
        return new RegisterCommand(fullName, email, phonePrefix, phoneNumber, rawPin);
    }
}