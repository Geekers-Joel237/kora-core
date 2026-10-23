package com.geekersjoel237.koracore.payment.ports.in;

import com.geekersjoel237.koracore.payment.application.command.CashInCommand;
import com.geekersjoel237.koracore.payment.application.result.PaymentResult;
import com.geekersjoel237.koracore.shared.ports.in.CommandHandler;

public interface CashInCommandHandler extends CommandHandler<CashInCommand, PaymentResult> {
}
