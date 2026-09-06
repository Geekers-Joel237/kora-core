package com.geekersjoel237.koracore.auth.adapters.in.rest.api.verifyOtp;

import com.geekersjoel237.koracore.auth.application.command.VerifyOtpCommand;
import com.geekersjoel237.koracore.auth.domain.vo.OtpCode;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

public record VerifyOtpRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "OTP code is required")
        @Pattern(regexp = "\\d{6}", message = "OTP code must be exactly 6 digits")
        String code
) {

    public VerifyOtpCommand toCommand(Id correlationId) {
        return new VerifyOtpCommand(correlationId, email, OtpCode.of(code));
    }
}