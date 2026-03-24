package com.geekersjoel237.koracore.application.command;

import com.geekersjoel237.koracore.domain.vo.Id;

public record CapturePaymentCommand(
        Id transactionId,
        Id customerId,
        String correlationId) {}