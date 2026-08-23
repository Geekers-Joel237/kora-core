package com.geekersjoel237.koracore.application.command;

import com.geekersjoel237.koracore.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;

public record CashInCommand(Id customerId, String rawPin, Amount amount, PaymentMethod paymentMethod) {
}