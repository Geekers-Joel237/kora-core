package com.geekersjoel237.koracore.payment.unit.doubles;

import com.geekersjoel237.koracore.payment.application.query.balance.BalanceResult;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.ports.out.query.AccountQueryPort;
import com.geekersjoel237.koracore.payment.ports.out.repository.AccountRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.util.Optional;

/**
 * Reads the same store the write side wrote to.
 *
 * <p>In production these are two different connections against two very different
 * statements, and nothing guarantees a read sees a write that has not committed. Here
 * they share a map, which is what lets a test assert the balance a cash-in produced
 * without standing up Postgres — the round trip itself is proved end to end.
 */
public class InMemoryAccountQueryPort implements AccountQueryPort {

    private final AccountRepository accounts;

    public InMemoryAccountQueryPort(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public Optional<BalanceResult> findBalance(Id customerId) {
        return accounts.findByCustomerId(customerId)
                .map(Account::snapshot)
                .map(snap -> new BalanceResult(
                        snap.accountId().value(),
                        snap.accountNumber(),
                        snap.balance().amount()));
    }

    @Override
    public Optional<Id> findWalletId(Id customerId) {
        return accounts.findByCustomerId(customerId)
                .map(account -> account.snapshot().accountId());
    }
}
