package com.geekersjoel237.koracore.payment.ports.in;

import com.geekersjoel237.koracore.payment.application.command.CashOutCommand;
import com.geekersjoel237.koracore.payment.application.result.PaymentResult;
import com.geekersjoel237.koracore.shared.ports.in.CommandHandler;

public interface CashOutCommandHandler extends CommandHandler<CashOutCommand, PaymentResult> {
}
