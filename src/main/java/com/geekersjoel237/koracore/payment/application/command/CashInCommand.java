package com.geekersjoel237.koracore.payment.application.command;

import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;
import com.geekersjoel237.koracore.shared.application.cqrs.Command;
import com.geekersjoel237.koracore.payment.application.result.PaymentResult;

public record CashInCommand(Id correlationId, Id customerId, Pin pin,
                            Amount amount, PaymentMethod paymentMethod)
        implements Command<PaymentResult> {
}