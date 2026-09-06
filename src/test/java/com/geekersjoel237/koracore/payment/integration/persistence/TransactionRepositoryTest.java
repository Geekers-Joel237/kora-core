package com.geekersjoel237.koracore.payment.integration.persistence;

import com.geekersjoel237.koracore.payment.adapters.out.persistence.JpaTransactionRepository;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.JpaTrxHistoricStatesRepository;
import com.geekersjoel237.koracore.payment.domain.enums.LedgerEntryType;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.payment.domain.model.LedgerEntry;
import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.payment.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.payment.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.entities.TransactionEntity;
import com.geekersjoel237.koracore.payment.adapters.out.persistence.repository.SpringDataTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import com.geekersjoel237.koracore.shared.integration.persistence.AbstractRepositoryTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionRepositoryTest extends AbstractRepositoryTest {

    private static final Amount AMOUNT_10K = new Amount(new BigDecimal("10000.00"), "XAF");
    @Autowired
    private JpaTransactionRepository txRepository;
    @Autowired
    private JpaTrxHistoricStatesRepository historicRepository;


    // ── helpers ───────────────────────────────────────────────────────────────
    @Autowired
    private SpringDataTransactionRepository springDataTransactionRepository;

    private Transaction buildTransaction(Id fromAccountId, Id toAccountId) {
        Id txId = Id.generate();
        Transaction tx = Transaction.create(txId, fromAccountId, toAccountId,
                TransactionType.CASH_IN, PaymentMethod.ORANGE_MONEY, AMOUNT_10K);
        tx.recordDoubleEntry(AMOUNT_10K, fromAccountId, toAccountId);
        return tx;
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void should_persist_transaction_with_two_operations_atomically() {
        Id fromAccountId = Id.generate();
        Id toAccountId = Id.generate();
        Transaction tx = buildTransaction(fromAccountId, toAccountId);

        txRepository.save(tx);

        Optional<Transaction> found = txRepository.findById(tx.snapshot().transactionId());
        assertThat(found).isPresent();
        assertThat(found.get().entries()).hasSize(2);
        assertThat(found.get().snapshot().amount().value())
                .isEqualByComparingTo(AMOUNT_10K.value());
    }

    @Test
    void should_rollback_on_constraint_violation_when_transaction_number_is_duplicated() {
        Id fromAccountId = Id.generate();
        Id toAccountId = Id.generate();
        Transaction tx = buildTransaction(fromAccountId, toAccountId);
        txRepository.save(tx);
        String duplicateNumber = tx.snapshot().transactionNumber();

        // Build a raw entity with the same transaction_number
        TransactionEntity duplicate = TransactionEntity.builder()
                .transactionNumber(duplicateNumber)
                .fromAccountId(fromAccountId.value())
                .toAccountId(toAccountId.value())
                .state(TransactionState.INITIALIZED.name())
                .type(TransactionType.CASH_IN)
                .paymentMethod(PaymentMethod.MOBILE_MONEY)
                .amount(AMOUNT_10K.value())
                .currency(AMOUNT_10K.currency())
                .occurredAt(java.time.Instant.now())
                .build();
        duplicate.setId(Id.generate().value());

        assertThatThrownBy(() -> springDataTransactionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_load_transaction_with_both_operations_by_id() {
        Id fromAccountId = Id.generate();
        Id toAccountId = Id.generate();
        Transaction tx = buildTransaction(fromAccountId, toAccountId);
        txRepository.save(tx);

        Transaction loaded = txRepository.findById(tx.snapshot().transactionId()).orElseThrow();

        List<LedgerEntry> ops = loaded.entries();
        assertThat(ops).hasSize(2);

        boolean hasDebit = ops.stream()
                .anyMatch(op -> op.snapshot().type() == LedgerEntryType.DEBIT
                        && op.snapshot().accountId().equals(fromAccountId));
        boolean hasCredit = ops.stream()
                .anyMatch(op -> op.snapshot().type() == LedgerEntryType.CREDIT
                        && op.snapshot().accountId().equals(toAccountId));

        assertThat(hasDebit).isTrue();
        assertThat(hasCredit).isTrue();
    }

    @Test
    void should_persist_transaction_state_historic() {
        Id fromAccountId = Id.generate();
        Id toAccountId = Id.generate();
        Transaction tx = buildTransaction(fromAccountId, toAccountId);
        txRepository.save(tx);

        TrxStateHistoric initialEntry = tx.history().getFirst();
        historicRepository.save(initialEntry);

        List<TrxStateHistoric> history =
                historicRepository.findByTransactionId(tx.snapshot().transactionId());

        assertThat(history).hasSize(1);
        assertThat(history.getFirst().oldState()).isNull();
        assertThat(history.getFirst().newState()).isEqualTo(TransactionState.INITIALIZED);
    }

    @Test
    void should_return_state_history_in_chronological_order() {
        Id fromAccountId = Id.generate();
        Id toAccountId = Id.generate();
        Transaction tx = buildTransaction(fromAccountId, toAccountId);
        txRepository.save(tx);

        // Record INITIALIZED → AUTHORIZED → COMPLETED in history
        historicRepository.save(tx.history().getFirst()); // INITIALIZED (null → INITIALIZED)
        tx.authorize();
        txRepository.save(tx);
        historicRepository.save(tx.history().get(1)); // INITIALIZED → AUTHORIZED
        tx.capture();
        tx.pendSettlement();
        tx.settle();
        tx.markCompleted();
        txRepository.save(tx);
        historicRepository.save(tx.history().getLast()); // SETTLED → COMPLETED

        List<TrxStateHistoric> history =
                historicRepository.findByTransactionId(tx.snapshot().transactionId());

        assertThat(history).hasSize(3);
        assertThat(history.get(0).newState()).isEqualTo(TransactionState.INITIALIZED);
        assertThat(history.get(1).newState()).isEqualTo(TransactionState.AUTHORIZED);
        assertThat(history.get(2).newState()).isEqualTo(TransactionState.COMPLETED);
        assertThat(history.get(0).oldState()).isNull();
        assertThat(history.get(1).oldState()).isEqualTo(TransactionState.INITIALIZED);
        assertThat(history.get(2).oldState()).isEqualTo(TransactionState.SETTLED);
    }
}