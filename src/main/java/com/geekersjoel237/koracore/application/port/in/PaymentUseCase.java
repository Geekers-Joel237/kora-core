package com.geekersjoel237.koracore.application.port.in;

import com.geekersjoel237.koracore.application.command.AuthorizePaymentCommand;
import com.geekersjoel237.koracore.application.command.CapturePaymentCommand;
import com.geekersjoel237.koracore.application.command.CashInCommand;
import com.geekersjoel237.koracore.application.command.CashOutCommand;
import com.geekersjoel237.koracore.application.command.ReversePaymentCommand;
import com.geekersjoel237.koracore.application.command.TransferCommand;
import com.geekersjoel237.koracore.domain.model.Account;
import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.vo.Id;

public interface PaymentUseCase {
    Transaction cashIn(CashInCommand cmd);
    Transaction cashOut(CashOutCommand cmd);
    Transaction transfer(TransferCommand cmd);
    Account getBalance(Id customerId);

    Transaction authorizePayment(AuthorizePaymentCommand cmd);
    Transaction capturePayment(CapturePaymentCommand cmd);
    Transaction reversePayment(ReversePaymentCommand cmd);
}