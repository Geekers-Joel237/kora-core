package com.geekersjoel237.koracore.payment.ports.in;

import com.geekersjoel237.koracore.payment.application.command.cashOut.CashOutCommand;
import com.geekersjoel237.koracore.payment.application.command.cashOut.CashOutResult;
import com.geekersjoel237.koracore.shared.ports.in.CommandHandler;

public interface CashOutCommandHandler extends CommandHandler<CashOutCommand, CashOutResult> {
}
