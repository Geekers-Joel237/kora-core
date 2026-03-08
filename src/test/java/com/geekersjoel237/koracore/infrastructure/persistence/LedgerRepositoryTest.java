package com.geekersjoel237.koracore.infrastructure.persistence;

import com.geekersjoel237.koracore.domain.model.Ledger;
import com.geekersjoel237.koracore.infrastructure.persistence.repository.JpaLedgerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private PostgresLedgerRepository repository;

    @Autowired
    private JpaLedgerRepository jpaRepository;

    @Test
    void should_return_the_bootstrapped_ledger_when_one_exists() {
        // The @PostConstruct in PostgresLedgerRepository ensures exactly one Ledger
        // row is committed to the DB before any test runs.
        Ledger ledger = repository.findFirst();

        assertThat(ledger).isNotNull();
        assertThat(ledger.snapshot().ledgerId()).isNotNull();
    }

    @Test
    void should_throw_illegal_state_when_no_ledger_exists() {
        // Soft-delete all existing ledger rows within this transaction
        jpaRepository.deleteAll();

        // Spring's @Repository translates IllegalStateException → InvalidDataAccessApiUsageException
        assertThatThrownBy(() -> repository.findFirst())
                .hasMessageContaining("No Ledger found");
    }
}