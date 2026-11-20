package com.geekersjoel237.koracore.payment.adapters.in.rest.api.transfer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

import static com.geekersjoel237.koracore.shared.adapters.in.rest.ApiSchemas.AMOUNT;

public record TransferRequest(
        @NotBlank(message = "PIN is required")
        String rawPin,

        @Schema(description = AMOUNT, multipleOf = 1)
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "1", message = "Amount must be at least 1 XAF")
        @Digits(integer = 15, fraction = 0,
                message = "XAF has no minor unit: amount must be a whole number")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code (e.g. XAF)")
        String currency,

        @NotBlank(message = "Recipient phone prefix is required")
        @Pattern(regexp = "\\+\\d{1,4}", message = "Recipient phone prefix must match +X to +XXXX (e.g. +225)")
        String toPhonePrefix,

        @NotBlank(message = "Recipient phone number is required")
        @Pattern(regexp = "\\d{8,15}", message = "Recipient phone number must contain 8 to 15 digits")
        String toPhoneNumber
) {
}