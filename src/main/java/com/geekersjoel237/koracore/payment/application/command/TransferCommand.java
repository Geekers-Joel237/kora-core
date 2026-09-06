package com.geekersjoel237.koracore.payment.application.command;

import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.Msisdn;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;
import com.geekersjoel237.koracore.shared.application.cqrs.Command;
import com.geekersjoel237.koracore.payment.application.result.PaymentResult;

public record TransferCommand(Id correlationId, Id customerId, Pin pin,
                              Amount amount, Msisdn recipient)
        implements Command<PaymentResult> {
}