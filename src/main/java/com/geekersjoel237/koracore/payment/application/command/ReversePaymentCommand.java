package com.geekersjoel237.koracore.payment.application.command;

import com.geekersjoel237.koracore.shared.application.cqrs.Command;
import com.geekersjoel237.koracore.payment.application.result.PaymentResult;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

public record ReversePaymentCommand(
        Id transactionId,
        Id actorId,
        String actorRole,
        String reason,
        Id correlationId) implements Command<PaymentResult> {
}