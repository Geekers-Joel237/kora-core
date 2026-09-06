package com.geekersjoel237.koracore.payment.application.usecases;

import com.geekersjoel237.koracore.payment.application.query.balance.BalanceQuery;
import com.geekersjoel237.koracore.payment.application.query.balance.BalanceResult;
import com.geekersjoel237.koracore.payment.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.payment.ports.in.BalanceQueryHandler;
import com.geekersjoel237.koracore.payment.ports.out.query.AccountQueryPort;


public final class BalanceService implements BalanceQueryHandler {

    private final AccountQueryPort accounts;

    public BalanceService(AccountQueryPort accounts) {
        this.accounts = accounts;
    }

    @Override
    public BalanceResult execute(BalanceQuery query) {
        return accounts.findBalance(query.customerId())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found for customer: " + query.customerId().value()));
    }
}
