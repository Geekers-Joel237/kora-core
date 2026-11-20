package com.geekersjoel237.koracore.payment.adapters.in.rest.api.reverse;

import jakarta.validation.constraints.NotBlank;

public record ReverseRequest(
        @NotBlank(message = "Actor ID is required")
        String actorId,

        @NotBlank(message = "Actor role is required")
        String actorRole,

        @NotBlank(message = "Reason is required for reversal — audit trail obligation")
        String reason,

        @NotBlank(message = "Correlation ID is required")
        String correlationId
) {
}