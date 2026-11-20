package com.geekersjoel237.koracore.payment.ports.in;

import com.geekersjoel237.koracore.payment.application.command.cashIn.CashInCommand;
import com.geekersjoel237.koracore.payment.application.command.cashIn.CashInResult;
import com.geekersjoel237.koracore.shared.ports.in.CommandHandler;

public interface CashInCommandHandler extends CommandHandler<CashInCommand, CashInResult> {
}
