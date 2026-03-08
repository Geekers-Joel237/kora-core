package com.geekersjoel237.koracore.infrastructure.persistence;

import com.geekersjoel237.koracore.domain.enums.ResourceType;
import com.geekersjoel237.koracore.domain.model.Account;
import com.geekersjoel237.koracore.domain.model.vo.AccountType;
import com.geekersjoel237.koracore.domain.port.AccountRepository;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Balance;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.infrastructure.persistence.entity.AccountEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.repository.JpaAccountRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PostgresAccountRepository implements AccountRepository {

    private final JpaAccountRepository jpaRepository;

    public PostgresAccountRepository(JpaAccountRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Account account) {
        jpaRepository.save(toEntity(account));
    }

    @Override
    public Optional<Account> findById(Id accountId) {
        return jpaRepository.findById(accountId.value()).map(this::toDomain);
    }

    @Override
    public Optional<Account> findByCustomerId(Id customerId) {
        return jpaRepository.findByResourceTypeAndResourceId(ResourceType.CUSTOMER_ACCOUNT, customerId.value())
                .map(this::toDomain);
    }

    @Override
    public Optional<Account> findFloatByProviderId(Id providerId) {
        return jpaRepository.findByResourceTypeAndResourceId(ResourceType.FLOAT_ACCOUNT, providerId.value())
                .map(this::toDomain);
    }

    private AccountEntity toEntity(Account account) {
        Account.Snapshot snapshot = account.snapshot();
        var entity = AccountEntity.builder()
                .accountNumber(snapshot.accountNumber())
                .resourceType(snapshot.accountType().resourceType())
                .resourceId(snapshot.accountType().resourceId().value())
                .balanceAmount(snapshot.balance().solde().value())
                .balanceCurrency(snapshot.balance().solde().currency())
                .isBlocked(snapshot.isBlocked())
                .build();
        entity.setId(snapshot.accountId().value());
        return entity;
    }

    private Account toDomain(AccountEntity entity) {
        return Account.createFromSnapshot(new Account.Snapshot(
                new Id(entity.getId()),
                entity.getAccountNumber(),
                new AccountType(new Id(entity.getResourceId()), entity.getResourceType()),
                new Balance(new Amount(entity.getBalanceAmount(), entity.getBalanceCurrency())),
                entity.isBlocked()
        ));
    }
}
