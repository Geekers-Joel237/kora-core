package com.geekersjoel237.koracore.application.command;

import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;

public record CashOutCommand(Id customerId, String rawPin, Amount amount, String paymentMethod) {
}