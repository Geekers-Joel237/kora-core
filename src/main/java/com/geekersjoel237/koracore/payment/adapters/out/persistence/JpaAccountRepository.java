package com.geekersjoel237.koracore.payment.adapters.out.persistence;

import com.geekersjoel237.koracore.payment.adapters.out.persistence.entities.AccountEntity;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.mapping.AccountPersistenceMapper;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.repository.SpringDataAccountRepository;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.repository.SpringDataFundsHoldRepository;
import com.geekersjoel237.koracore.payment.domain.enums.HoldStatus;
import com.geekersjoel237.koracore.payment.domain.enums.ResourceType;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.ports.out.repository.AccountRepository;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaAccountRepository implements AccountRepository {

    private final SpringDataAccountRepository jpaRepository;
    private final SpringDataFundsHoldRepository holdRepository;
    private final AccountPersistenceMapper mapper;

    public JpaAccountRepository(SpringDataAccountRepository jpaRepository,
                                SpringDataFundsHoldRepository holdRepository,
                                AccountPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.holdRepository = holdRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(Account account) {
        Account.Snapshot snapshot = account.snapshot();
        AccountEntity entity = jpaRepository.findById(snapshot.accountId().value()).orElse(null);
        if (entity == null) {
            entity = mapper.toEntity(snapshot);
        } else {
            Amount balance = snapshot.balance().solde();
            entity.update(balance.value(), balance.currency(), snapshot.isBlocked());
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

    private Account toDomain(AccountEntity entity) {
        String currency = entity.getBalanceCurrency();
        Amount reserved = new Amount(holdRepository.sumAmount(entity.getId(), currency, HoldStatus.ACTIVE), currency);
        return Account.createFromSnapshot(mapper.toSnapshot(entity, reserved));
    }
}
