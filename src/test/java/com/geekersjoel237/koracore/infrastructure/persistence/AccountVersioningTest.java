package com.geekersjoel237.koracore.infrastructure.persistence;

import com.geekersjoel237.koracore.domain.model.Account;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.infrastructure.persistence.entities.AccountEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.repository.SpringDataAccountRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountVersioningTest extends AbstractRepositoryTest {

    @Autowired
    private JpaAccountRepository repository;

    @Autowired
    private SpringDataAccountRepository jpaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // ── version increment ─────────────────────────────────────────────────────

    @Test
    void version_field_increments_on_each_update() {
        Id accountId = Id.generate();
        Id customerId = Id.generate();
        Account account = Account.createCustomerAccount(accountId, customerId);

        // First save → INSERT, version initialised to 0 by Hibernate
        repository.save(account);
        jpaRepository.flush();
        assertThat(jpaRepository.findById(accountId.value()).orElseThrow().getVersion())
                .isEqualTo(0L);

        // Second save → UPDATE, version incremented to 1
        account.credit(Amount.of(BigDecimal.valueOf(100), "XAF"));
        repository.save(account);
        jpaRepository.flush();
        assertThat(jpaRepository.findById(accountId.value()).orElseThrow().getVersion())
                .isEqualTo(1L);

        // Third save → UPDATE, version incremented to 2
        account.credit(Amount.of(BigDecimal.valueOf(50), "XAF"));
        repository.save(account);
        jpaRepository.flush();
        assertThat(jpaRepository.findById(accountId.value()).orElseThrow().getVersion())
                .isEqualTo(2L);
    }

    // ── concurrent update conflict ────────────────────────────────────────────

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrent_update_throws_optimistic_locking_failure() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // 1. Insert the account in its own committed transaction
        Id accountId = Id.generate();
        Id customerId = Id.generate();
        tx.execute(status -> {
            Account account = Account.createCustomerAccount(accountId, customerId);
            repository.save(account);
            return null;
        });

        // 2. Load the entity in tx-A (version=0), do NOT save yet — keep the stale entity
        AccountEntity staleEntity = tx.execute(status -> {
            status.setRollbackOnly();          // load only, roll back
            return jpaRepository.findById(accountId.value()).orElseThrow();
        });

        // 3. tx-B saves first — version goes 0 → 1 (committed)
        tx.execute(status -> {
            AccountEntity fresh = jpaRepository.findById(accountId.value()).orElseThrow();
            fresh.setBalanceAmount(BigDecimal.valueOf(500));
            jpaRepository.saveAndFlush(fresh);
            return null;
        });

        // 4. tx-A now tries to save with stale version=0 — must throw
        assertThatThrownBy(() -> tx.execute(status -> {
            Assertions.assertNotNull(staleEntity);
            staleEntity.setBalanceAmount(BigDecimal.valueOf(999));
            jpaRepository.saveAndFlush(staleEntity);
            return null;
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}