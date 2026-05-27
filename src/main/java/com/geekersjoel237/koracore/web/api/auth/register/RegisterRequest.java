package com.geekersjoel237.koracore.web.api.auth.register;

import com.geekersjoel237.koracore.application.command.RegisterCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Phone prefix is required")
        @Pattern(regexp = "\\+\\d{1,4}", message = "Phone prefix must match +X to +XXXX (e.g. +225)")
        String phonePrefix,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "\\d{8,15}", message = "Phone number must contain 8 to 15 digits")
        String phoneNumber,

        @NotBlank(message = "PIN is required")
        @Size(min = 4, max = 8, message = "PIN must be between 4 and 8 characters")
        String rawPin
) {
    public RegisterCommand toCommand() {
        return new RegisterCommand(fullName, email, phonePrefix, phoneNumber, rawPin);
    }
}