package com.geekersjoel237.koracore.domain.port;

import com.geekersjoel237.koracore.domain.model.Account;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.util.Optional;

public interface AccountRepository {
    void save(Account account);
    Optional<Account> findById(Id accountId);
    Optional<Account> findByCustomerId(Id customerId);
    Optional<Account> findFloatByProviderId(Id providerId);
}