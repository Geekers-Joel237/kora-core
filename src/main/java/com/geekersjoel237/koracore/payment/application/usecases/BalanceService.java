package com.geekersjoel237.koracore.payment.application.usecases;

import com.geekersjoel237.koracore.payment.application.query.balance.BalanceQuery;
import com.geekersjoel237.koracore.payment.application.query.balance.BalanceResult;
import com.geekersjoel237.koracore.payment.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.payment.ports.in.BalanceQueryHandler;
import com.geekersjoel237.koracore.payment.ports.out.query.ReadAccountRepository;


public final class BalanceService implements BalanceQueryHandler {

    private final ReadAccountRepository repository;

    public BalanceService(ReadAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public BalanceResult execute(BalanceQuery query) {
        return repository.findBalance(query.customerId())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found for customer: " + query.customerId().value()));
    }
}
