package com.geekersjoel237.koracore.shared.inmemory;

import com.geekersjoel237.koracore.domain.enums.ResourceType;
import com.geekersjoel237.koracore.domain.model.Account;
import com.geekersjoel237.koracore.domain.port.AccountRepository;
import com.geekersjoel237.koracore.domain.vo.Id;

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
    public Optional<Account> findFloatByProviderId(Id providerId) {
        return store.values().stream()
                .filter(a -> a.snapshot().accountType().resourceType() == ResourceType.FLOAT_ACCOUNT
                        && a.snapshot().accountType().resourceId().equals(providerId))
                .findFirst();
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