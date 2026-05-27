package com.geekersjoel237.koracore.application.command;

import com.geekersjoel237.koracore.domain.vo.Id;

public record ReversePaymentCommand(
        Id transactionId,
        String actorId,
        String actorRole,
        String reason,
        String correlationId) {}