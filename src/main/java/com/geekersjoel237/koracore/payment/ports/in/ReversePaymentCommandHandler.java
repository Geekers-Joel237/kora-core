package com.geekersjoel237.koracore.payment.ports.in;

import com.geekersjoel237.koracore.payment.application.command.reversePayment.ReversePaymentCommand;
import com.geekersjoel237.koracore.payment.application.command.reversePayment.ReversePaymentResult;
import com.geekersjoel237.koracore.shared.ports.in.CommandHandler;

public interface ReversePaymentCommandHandler extends CommandHandler<ReversePaymentCommand, ReversePaymentResult> {
}
