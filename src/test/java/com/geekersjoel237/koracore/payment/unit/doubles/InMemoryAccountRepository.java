package com.geekersjoel237.koracore.payment.unit.doubles;

import com.geekersjoel237.koracore.payment.domain.enums.ResourceType;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.ports.out.repository.AccountRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryAccountRepository implements AccountRepository {

    private final Map<String, Account> store = new HashMap<>();

    @Override
    public void save(Account account) {
        store.put(account.snapshot().accountId().value(), account);
    }

    @Override
    public Optional<Account> findById(Id accountId) {
        return Optional.ofNullable(store.get(accountId.value()));
    }

    @Override
    public Optional<Account> findByCustomerId(Id customerId) {
        return store.values().stream()
                .filter(a -> a.snapshot().accountType().resourceType() == ResourceType.CUSTOMER_ACCOUNT
                        && a.snapshot().accountType().resourceId().equals(customerId))
                .findFirst();
    }

    @Override
    public Optional<Account> findByCustomerIdForUpdate(Id customerId) {
        return findByCustomerId(customerId);
    }

    @Override
    public Optional<Account> findFloatByProviderId(Id providerId) {
        return store.values().stream()
                .filter(a -> a.snapshot().accountType().resourceType() == ResourceType.FLOAT_ACCOUNT
                        && a.snapshot().accountType().resourceId().equals(providerId))
                .findFirst();
    }

    @Override
    public Optional<Account> findFloatByProviderIdForUpdate(Id providerId) {
        // In-memory: no locking needed — delegate to the same logic
        return findFloatByProviderId(providerId);
    }

    public void reset() {
        store.clear();
    }

    public void preload(Account... accounts) {
        for (Account account : accounts) {
            save(account);
        }
    }
}