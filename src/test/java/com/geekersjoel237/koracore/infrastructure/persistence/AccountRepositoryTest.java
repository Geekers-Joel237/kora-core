package com.geekersjoel237.koracore.infrastructure.persistence;

import com.geekersjoel237.koracore.domain.enums.ResourceType;
import com.geekersjoel237.koracore.domain.model.Account;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.infrastructure.persistence.entity.AccountEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.repository.JpaAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class AccountRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private PostgresAccountRepository repository;

    @Autowired
    private JpaAccountRepository jpaRepository;

    @Test
    void should_persist_and_return_intact_when_saving_customer_account() {
        Id accountId = Id.generate();
        Id customerId = Id.generate();
        Account account = Account.createCustomerAccount(accountId, customerId);
        account.credit(new Amount(new BigDecimal("100.00"), "XOF"));

        repository.save(account);

        Optional<Account> found = repository.findById(accountId);
        assertThat(found).isPresent();
        assertThat(found.get().snapshot().accountId()).isEqualTo(accountId);
        assertThat(found.get().snapshot().accountType().resourceId()).isEqualTo(customerId);
        assertThat(found.get().snapshot().balance().solde().value()).isEqualByComparingTo("100.00");
    }

    @Test
    void should_persist_with_correct_resource_id_when_saving_float_account() {
        Id accountId = Id.generate();
        Id providerId = Id.generate();
        Account account = Account.createFloatAccount(accountId, providerId);

        repository.save(account);

        Optional<Account> found = repository.findById(accountId);
        assertThat(found).isPresent();
        assertThat(found.get().snapshot().accountType().resourceId()).isEqualTo(providerId);
    }

    @Test
    void should_return_empty_when_account_not_found() {
        Optional<Account> found = repository.findById(Id.generate());
        assertThat(found).isEmpty();
    }

    @Test
    void should_return_customer_account_when_finding_by_customer_id() {
        Id accountId = Id.generate();
        Id customerId = Id.generate();
        Account account = Account.createCustomerAccount(accountId, customerId);
        repository.save(account);

        Optional<Account> found = repository.findByCustomerId(customerId);
        assertThat(found).isPresent();
        assertThat(found.get().snapshot().accountId()).isEqualTo(accountId);
    }

    @Test
    void should_return_float_account_when_finding_by_provider_id() {
        Id accountId = Id.generate();
        Id providerId = Id.generate();
        Account account = Account.createFloatAccount(accountId, providerId);
        repository.save(account);

        Optional<Account> found = repository.findFloatByProviderId(providerId);
        assertThat(found).isPresent();
        assertThat(found.get().snapshot().accountId()).isEqualTo(accountId);
    }

    @Test
    void should_throw_data_integrity_violation_exception_when_account_number_is_duplicated() {
        Id accountId1 = Id.generate();
        Id customerId1 = Id.generate();
        Account account1 = Account.createCustomerAccount(accountId1, customerId1);
        repository.save(account1);

        AccountEntity duplicate = new AccountEntity();
        duplicate.setId("fake-id");
        duplicate.setAccountNumber(account1.snapshot().accountNumber());
        duplicate.setResourceType(ResourceType.CUSTOMER_ACCOUNT);
        duplicate.setResourceId("fake-res");
        duplicate.setBalanceAmount(BigDecimal.ZERO);
        duplicate.setBalanceCurrency("XOF");
        duplicate.setBlocked(false);

        assertThatThrownBy(() -> jpaRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_throw_data_integrity_violation_exception_when_two_accounts_have_same_customer_id() {
        Id accountId1 = Id.generate();
        Id customerId = Id.generate();
        Account account1 = Account.createCustomerAccount(accountId1, customerId);
        repository.save(account1);

        // Build a raw entity with the same resource_type + resource_id and force an
        // immediate flush so the DB unique constraint fires within the test transaction.
        AccountEntity duplicate = new AccountEntity();
        duplicate.setId(Id.generate().value());
        duplicate.setAccountNumber("ACC-DUPLICATE-" + Id.generate().value().substring(0, 4));
        duplicate.setResourceType(ResourceType.CUSTOMER_ACCOUNT);
        duplicate.setResourceId(customerId.value());   // same resource_id → constraint violation
        duplicate.setBalanceAmount(BigDecimal.ZERO);
        duplicate.setBalanceCurrency("XOF");
        duplicate.setBlocked(false);

        assertThatThrownBy(() -> jpaRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_preserve_amount_precision_when_persisting_balance() {
        Id accountId = Id.generate();
        Id customerId = Id.generate();
        Account account = Account.createCustomerAccount(accountId, customerId);

        BigDecimal val1 = new BigDecimal("0.1");
        BigDecimal val2 = new BigDecimal("0.2");
        BigDecimal sum = val1.add(val2);

        account.credit(new Amount(sum, "XOF"));
        repository.save(account);

        Account found = repository.findById(accountId).orElseThrow();
        assertThat(found.snapshot().balance().solde().value()).isEqualByComparingTo("0.3");

        BigDecimal large = new BigDecimal("999999999.99");
        account.credit(new Amount(large, "XOF"));
        repository.save(account);

        found = repository.findById(accountId).orElseThrow();
        assertThat(found.snapshot().balance().solde().value()).isEqualByComparingTo(sum.add(large));
    }
}