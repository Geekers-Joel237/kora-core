package com.geekersjoel237.koracore.infrastructure.persistence;

import com.geekersjoel237.koracore.domain.enums.OperationType;
import com.geekersjoel237.koracore.domain.enums.TransactionType;
import com.geekersjoel237.koracore.domain.model.Operation;
import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.model.TrxStateHistoric;
import com.geekersjoel237.koracore.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.infrastructure.persistence.entity.TransactionEntity;
import com.geekersjoel237.koracore.infrastructure.persistence.repository.JpaTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionRepositoryTest extends AbstractRepositoryTest {

    private static final Amount AMOUNT_10K = new Amount(new BigDecimal("10000.00"), "XOF");
    @Autowired
    private PostgresTransactionRepository txRepository;
    @Autowired
    private PostgresTrxHistoricStatesRepository historicRepository;


    // ── helpers ───────────────────────────────────────────────────────────────
    @Autowired
    private JpaTransactionRepository jpaTransactionRepository;

    private Transaction buildTransaction(Id fromId, Id toId) {
        Id txId = Id.generate();
        String txNumber = "TRX-" + txId.value().substring(0, 8).toUpperCase();
        Transaction tx = Transaction.create(txId, txNumber, fromId, toId,
                TransactionType.CASH_IN, "ORANGE_MONEY", AMOUNT_10K);
        tx.addOperation(Operation.create(Id.generate(), OperationType.DEBIT, AMOUNT_10K, fromId));
        tx.addOperation(Operation.create(Id.generate(), OperationType.CREDIT, AMOUNT_10K, toId));
        return tx;
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void should_persist_transaction_with_two_operations_atomically() {
        Id fromId = Id.generate();
        Id toId = Id.generate();
        Transaction tx = buildTransaction(fromId, toId);

        txRepository.save(tx);

        Optional<Transaction> found = txRepository.findById(tx.snapshot().transactionId());
        assertThat(found).isPresent();
        assertThat(found.get().operations()).hasSize(2);
        assertThat(found.get().snapshot().amount().value())
                .isEqualByComparingTo(AMOUNT_10K.value());
    }

    @Test
    void should_rollback_on_constraint_violation_when_transaction_number_is_duplicated() {
        Id fromId = Id.generate();
        Id toId = Id.generate();
        Transaction tx = buildTransaction(fromId, toId);
        txRepository.save(tx);
        String duplicateNumber = tx.snapshot().transactionNumber();

        // Build a raw entity with the same transaction_number
        TransactionEntity duplicate = TransactionEntity.builder()
                .transactionNumber(duplicateNumber)
                .fromId(fromId.value())
                .toId(toId.value())
                .state(TransactionState.INITIALIZED.name())
                .type(TransactionType.CASH_IN)
                .paymentMethod("MTN")
                .amount(AMOUNT_10K.value())
                .currency(AMOUNT_10K.currency())
                .occurredAt(java.time.Instant.now())
                .build();
        duplicate.setId(Id.generate().value());

        assertThatThrownBy(() -> jpaTransactionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_load_transaction_with_both_operations_by_id() {
        Id fromId = Id.generate();
        Id toId = Id.generate();
        Transaction tx = buildTransaction(fromId, toId);
        txRepository.save(tx);

        Transaction loaded = txRepository.findById(tx.snapshot().transactionId()).orElseThrow();

        List<Operation> ops = loaded.operations();
        assertThat(ops).hasSize(2);

        boolean hasDebit = ops.stream()
                .anyMatch(op -> op.snapshot().type() == OperationType.DEBIT
                        && op.snapshot().accountId().equals(fromId));
        boolean hasCredit = ops.stream()
                .anyMatch(op -> op.snapshot().type() == OperationType.CREDIT
                        && op.snapshot().accountId().equals(toId));

        assertThat(hasDebit).isTrue();
        assertThat(hasCredit).isTrue();
    }

    @Test
    void should_find_transactions_by_account_id_for_two_different_accounts() {
        Id accountA = Id.generate();
        Id accountB = Id.generate();
        Id accountC = Id.generate();

        Transaction tx1 = buildTransaction(accountA, accountB);
        Transaction tx2 = buildTransaction(accountA, accountC);
        txRepository.save(tx1);
        txRepository.save(tx2);

        List<Transaction> forA = txRepository.findByAccountId(accountA);
        List<Transaction> forB = txRepository.findByAccountId(accountB);
        List<Transaction> forC = txRepository.findByAccountId(accountC);

        assertThat(forA).hasSize(2);
        assertThat(forB).hasSize(1);
        assertThat(forC).hasSize(1);
    }

    @Test
    void should_return_empty_when_no_transactions_for_account() {
        List<Transaction> result = txRepository.findByAccountId(Id.generate());
        assertThat(result).isEmpty();
    }

    @Test
    void should_filter_transactions_to_only_those_for_given_account() {
        Id accountX = Id.generate();
        Id accountY = Id.generate();
        Id accountZ = Id.generate();

        // Only one transaction involves accountX
        txRepository.save(buildTransaction(accountX, accountY));
        // This one does NOT involve accountX
        txRepository.save(buildTransaction(accountY, accountZ));

        List<Transaction> forX = txRepository.findByAccountId(accountX);
        assertThat(forX).hasSize(1);
        Transaction tx = forX.getFirst();
        assertThat(tx.snapshot().fromId()).isEqualTo(accountX);
    }

    @Test
    void should_persist_transaction_state_historic() {
        Id fromId = Id.generate();
        Id toId = Id.generate();
        Transaction tx = buildTransaction(fromId, toId);
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
        Id fromId = Id.generate();
        Id toId = Id.generate();
        Transaction tx = buildTransaction(fromId, toId);
        txRepository.save(tx);

        // Record INITIALIZED → PENDING → COMPLETED in history
        historicRepository.save(tx.history().getFirst()); // INITIALIZED (null → INITIALIZED)
        tx.markPending();
        txRepository.save(tx);
        historicRepository.save(tx.history().get(1)); // INITIALIZED → PENDING
        tx.markCompleted();
        txRepository.save(tx);
        historicRepository.save(tx.history().get(2)); // PENDING → COMPLETED

        List<TrxStateHistoric> history =
                historicRepository.findByTransactionId(tx.snapshot().transactionId());

        assertThat(history).hasSize(3);
        assertThat(history.get(0).newState()).isEqualTo(TransactionState.INITIALIZED);
        assertThat(history.get(1).newState()).isEqualTo(TransactionState.PENDING);
        assertThat(history.get(2).newState()).isEqualTo(TransactionState.COMPLETED);
        assertThat(history.get(0).oldState()).isNull();
        assertThat(history.get(1).oldState()).isEqualTo(TransactionState.INITIALIZED);
        assertThat(history.get(2).oldState()).isEqualTo(TransactionState.PENDING);
    }
}