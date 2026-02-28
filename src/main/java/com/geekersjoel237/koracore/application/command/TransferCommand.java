package com.geekersjoel237.koracore.application.command;

import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;

public record TransferCommand(Id customerId, String rawPin, Amount amount,
                               String paymentMethod, String toPhoneNumber) {
}