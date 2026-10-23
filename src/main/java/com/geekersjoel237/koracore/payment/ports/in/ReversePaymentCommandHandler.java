package com.geekersjoel237.koracore.payment.ports.in;

import com.geekersjoel237.koracore.payment.application.command.ReversePaymentCommand;
import com.geekersjoel237.koracore.payment.application.result.PaymentResult;
import com.geekersjoel237.koracore.shared.ports.in.CommandHandler;

public interface ReversePaymentCommandHandler extends CommandHandler<ReversePaymentCommand, PaymentResult> {
}
