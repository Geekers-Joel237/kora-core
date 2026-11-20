package com.geekersjoel237.koracore.payment.application.command.transfer;

import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.PhoneNumber;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;
import com.geekersjoel237.koracore.shared.application.cqrs.Command;
import com.geekersjoel237.koracore.payment.application.command.transfer.TransferResult;

public record TransferCommand(Id correlationId, Id customerId, Pin pin,
                              Amount amount, PhoneNumber recipient)
        implements Command<TransferResult> {
}