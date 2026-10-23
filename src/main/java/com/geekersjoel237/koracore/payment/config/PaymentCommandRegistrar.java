package com.geekersjoel237.koracore.payment.config;

import com.geekersjoel237.koracore.payment.application.command.CashInCommand;
import com.geekersjoel237.koracore.payment.application.command.ExpireAuthorizationsCommand;
import com.geekersjoel237.koracore.payment.application.command.CashOutCommand;
import com.geekersjoel237.koracore.payment.application.command.ReversePaymentCommand;
import com.geekersjoel237.koracore.payment.application.command.TransferCommand;
import com.geekersjoel237.koracore.payment.ports.in.CashInCommandHandler;
import com.geekersjoel237.koracore.payment.ports.in.ExpireAuthorizationsCommandHandler;
import com.geekersjoel237.koracore.payment.ports.in.CashOutCommandHandler;
import com.geekersjoel237.koracore.payment.ports.in.ReversePaymentCommandHandler;
import com.geekersjoel237.koracore.payment.ports.in.TransferCommandHandler;
import com.geekersjoel237.koracore.shared.adapters.in.cqrs.CommandRegistrar;
import com.geekersjoel237.koracore.shared.adapters.in.cqrs.CommandRegistry;
import org.springframework.stereotype.Component;


@Component
public class PaymentCommandRegistrar implements CommandRegistrar {

    private final CashInCommandHandler cashIn;
    private final CashOutCommandHandler cashOut;
    private final TransferCommandHandler transfer;
    private final ReversePaymentCommandHandler reversePayment;
    private final ExpireAuthorizationsCommandHandler expireAuthorizations;

    public PaymentCommandRegistrar(CashInCommandHandler cashIn, CashOutCommandHandler cashOut,
                                   TransferCommandHandler transfer, ReversePaymentCommandHandler reversePayment,
                                   ExpireAuthorizationsCommandHandler expireAuthorizations) {
        this.cashIn = cashIn;
        this.cashOut = cashOut;
        this.transfer = transfer;
        this.reversePayment = reversePayment;
        this.expireAuthorizations = expireAuthorizations;
    }

    @Override
    public void registerInto(CommandRegistry registry) {
        registry.register(CashInCommand.class, cashIn)
                .register(CashOutCommand.class, cashOut)
                .register(TransferCommand.class, transfer)
                .register(ReversePaymentCommand.class, reversePayment)
                .register(ExpireAuthorizationsCommand.class, expireAuthorizations);
    }
}
