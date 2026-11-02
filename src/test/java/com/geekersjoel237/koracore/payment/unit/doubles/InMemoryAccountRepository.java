package com.geekersjoel237.koracore.payment.unit.doubles;

import com.geekersjoel237.koracore.payment.domain.enums.ResourceType;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.ports.out.repository.AccountRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public class InMemoryAccountRepository implements AccountRepository {

    private final Map<Id, Account.Snapshot> store = new HashMap<>();
    private final InMemoryFundsHoldRepository holds;

    public InMemoryAccountRepository() {
        this(new InMemoryFundsHoldRepository());
    }

    public InMemoryAccountRepository(InMemoryFundsHoldRepository holds) {
        this.holds = holds;
    }

    @Override
    public void save(Account account) {
        Account.Snapshot snapshot = account.snapshot();
        store.put(snapshot.accountId(), snapshot);
    }

    @Override
    public Optional<Account> findById(Id accountId) {
        return Optional.ofNullable(store.get(accountId)).map(this::load);
    }

    @Override
    public Optional<Account> findByIdForUpdate(Id accountId) {
        return findById(accountId);
    }

    @Override
    public Optional<Account> findByCustomerId(Id customerId) {
        return find(snap -> snap.accountType().resourceType() == ResourceType.CUSTOMER_ACCOUNT
                && snap.accountType().resourceId().equals(customerId));
    }

    @Override
    public Optional<Account> findByCustomerIdForUpdate(Id customerId) {
        return findByCustomerId(customerId);
    }

    @Override
    public Optional<Account> findFloatByProviderId(Id providerId) {
        return find(snap -> snap.accountType().resourceType() == ResourceType.FLOAT_ACCOUNT
                && snap.accountType().resourceId().equals(providerId));
    }

    private Optional<Account> find(Predicate<Account.Snapshot> matches) {
        return store.values().stream().filter(matches).findFirst().map(this::load);
    }

    private Account load(Account.Snapshot snap) {
        return Account.createFromSnapshot(new Account.Snapshot(
                snap.accountId(), snap.accountNumber(), snap.accountType(), snap.balance(),
                holds.activeTotalOn(snap.accountId(), snap.balance().solde().currency()),
                snap.isBlocked()));
    }
}
