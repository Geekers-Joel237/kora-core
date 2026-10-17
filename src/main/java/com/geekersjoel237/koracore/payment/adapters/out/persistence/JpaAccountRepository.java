package com.geekersjoel237.koracore.payment.adapters.out.persistence;

import com.geekersjoel237.koracore.payment.domain.enums.ResourceType;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.domain.vo.AccountType;
import com.geekersjoel237.koracore.payment.ports.out.repository.AccountRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.payment.domain.vo.Balance;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.entities.AccountEntity;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.repository.SpringDataAccountRepository;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.repository.SpringDataFundsHoldRepository;
import com.geekersjoel237.koracore.payment.domain.enums.HoldStatus;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaAccountRepository implements AccountRepository {

    private final SpringDataAccountRepository jpaRepository;
    private final SpringDataFundsHoldRepository holdRepository;

    public JpaAccountRepository(SpringDataAccountRepository jpaRepository,
                                SpringDataFundsHoldRepository holdRepository) {
        this.jpaRepository = jpaRepository;
        this.holdRepository = holdRepository;
    }

    @Override
    public void save(Account account) {
        Account.Snapshot snap = account.snapshot();
        String id = snap.accountId().value();

        AccountEntity entity = jpaRepository.findById(id).orElse(null);
        if (entity == null) {
            entity = toEntity(account);
        } else {
            // Update mutable fields on the already-managed entity.
            // This avoids merging a null-version builder entity into the session,
            // which would corrupt Hibernate's optimistic-lock snapshot.
            entity.update(snap);
        }
        jpaRepository.save(entity);
    }

    @Override
    public Optional<Account> findById(Id accountId) {
        return jpaRepository.findById(accountId.value()).map(this::toDomain);
    }

    @Override
    public Optional<Account> findByIdForUpdate(Id accountId) {
        return jpaRepository.findByIdForUpdate(accountId.value()).map(this::toDomain);
    }

    @Override
    public Optional<Account> findByCustomerId(Id customerId) {
        return jpaRepository.findByResourceTypeAndResourceId(ResourceType.CUSTOMER_ACCOUNT, customerId.value())
                .map(this::toDomain);
    }

    @Override
    public Optional<Account> findByCustomerIdForUpdate(Id customerId) {
        return jpaRepository.findCustomerAccountForUpdate(customerId.value())
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
        String currency = entity.getBalanceCurrency();
        return Account.createFromSnapshot(new Account.Snapshot(
                new Id(entity.getId()),
                entity.getAccountNumber(),
                new AccountType(new Id(entity.getResourceId()), entity.getResourceType()),
                new Balance(new Amount(entity.getBalanceAmount(), currency)),
                new Amount(holdRepository.sumAmount(entity.getId(), currency, HoldStatus.ACTIVE), currency),
                entity.isBlocked()
        ));
    }
}
