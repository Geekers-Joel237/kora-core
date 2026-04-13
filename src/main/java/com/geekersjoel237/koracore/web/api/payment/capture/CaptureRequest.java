package com.geekersjoel237.koracore.web.api.payment.capture;

import com.geekersjoel237.koracore.application.command.CapturePaymentCommand;
import com.geekersjoel237.koracore.domain.vo.Id;

public record CaptureRequest(String correlationId) {

    public CapturePaymentCommand toCommand(Id transactionId, Id customerId) {
        return new CapturePaymentCommand(transactionId, customerId, correlationId);
    }
}