package com.geekersjoel237.koracore.payment.ports.in;

import com.geekersjoel237.koracore.payment.application.command.transfer.TransferCommand;
import com.geekersjoel237.koracore.payment.application.command.transfer.TransferResult;
import com.geekersjoel237.koracore.shared.ports.in.CommandHandler;

public interface TransferCommandHandler extends CommandHandler<TransferCommand, TransferResult> {
}
