package com.geekersjoel237.koracore.payment.ports.out.repository;

import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.util.Optional;

public interface AccountRepository {
    void save(Account account);
    Optional<Account> findById(Id accountId);
    Optional<Account> findByCustomerId(Id customerId);
    Optional<Account> findByCustomerIdForUpdate(Id customerId);
    Optional<Account> findFloatByProviderId(Id providerId);
    Optional<Account> findFloatByProviderIdForUpdate(Id providerId);
}