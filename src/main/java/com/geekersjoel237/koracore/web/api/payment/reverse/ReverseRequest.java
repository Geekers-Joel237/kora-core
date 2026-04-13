package com.geekersjoel237.koracore.web.api.payment.reverse;

import com.geekersjoel237.koracore.application.command.ReversePaymentCommand;
import com.geekersjoel237.koracore.domain.vo.Id;

public record ReverseRequest(
        String actorId,
        String actorRole,
        String reason,
        String correlationId) {

    public ReversePaymentCommand toCommand(Id transactionId) {
        return new ReversePaymentCommand(transactionId, actorId, actorRole, reason, correlationId);
    }
}