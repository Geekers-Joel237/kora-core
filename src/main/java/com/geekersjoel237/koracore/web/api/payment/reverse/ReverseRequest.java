package com.geekersjoel237.koracore.web.api.payment.reverse;

import com.geekersjoel237.koracore.application.command.ReversePaymentCommand;
import com.geekersjoel237.koracore.domain.vo.Id;
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
    public ReversePaymentCommand toCommand(Id transactionId) {
        return new ReversePaymentCommand(transactionId, actorId, actorRole, reason, correlationId);
    }
}