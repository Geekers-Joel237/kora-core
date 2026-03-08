package com.geekersjoel237.koracore.domain.model;

import com.geekersjoel237.koracore.domain.enums.OperationType;
import com.geekersjoel237.koracore.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.domain.enums.TransactionType;
import com.geekersjoel237.koracore.domain.exception.InvalidStateTransitionException;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {

    private static final Amount AMT = Amount.of(BigDecimal.valueOf(100), "XOF");

    private Transaction createTestTransaction() {
        return Transaction.create(
                Id.generate(), "TRX-001",
                new Id("from-001"), new Id("to-001"),
                TransactionType.CASH_IN, "MOBILE", AMT
        );
    }

    private Operation testOperation() {
        return Operation.create(Id.generate(), OperationType.DEBIT, AMT, new Id("acc-001"));
    }

    // ── Construction ──────────────────────────────────────────────────────────

    @Test
    void should_create_transaction_with_initialized_state() {
        assertEquals(TransactionState.INITIALIZED, createTestTransaction().snapshot().state());
    }

    @Test
    void should_create_transaction_with_empty_operations() {
        assertTrue(createTestTransaction().operations().isEmpty());
    }

    @Test
    void should_record_initial_historic_state_on_creation() {
        Transaction tx = createTestTransaction();
        assertEquals(1, tx.snapshot().history().size());
        assertEquals(TransactionState.INITIALIZED, tx.snapshot().history().getFirst().newState());
    }

    @Test
    void should_store_from_and_to_ids_correctly() {
        Transaction tx = Transaction.create(
                Id.generate(), "TRX-001",
                new Id("from-001"), new Id("to-001"),
                TransactionType.CASH_IN, "MOBILE", AMT);
        assertEquals(new Id("from-001"), tx.snapshot().fromId());
        assertEquals(new Id("to-001"),  tx.snapshot().toId());
    }

    // ── addOperation ──────────────────────────────────────────────────────────

    @Test
    void should_add_operation_when_valid() {
        Transaction tx = createTestTransaction();
        tx.addOperation(testOperation());
        assertEquals(1, tx.operations().size());
    }

    @Test
    void should_return_unmodifiable_operations_list() {
        Transaction tx = createTestTransaction();
        assertThrows(UnsupportedOperationException.class,
                () -> tx.operations().add(testOperation()));
    }

    // ── transitionTo ──────────────────────────────────────────────────────────

    @Test
    void should_transition_to_pending_from_initialized() {
        Transaction tx = createTestTransaction();
        assertDoesNotThrow(tx::markPending);
        assertEquals(TransactionState.PENDING, tx.snapshot().state());
    }

    @Test
    void should_transition_to_completed_from_pending() {
        Transaction tx = createTestTransaction();
        tx.markPending();
        assertDoesNotThrow(tx::markCompleted);
        assertEquals(TransactionState.COMPLETED, tx.snapshot().state());
    }

    @Test
    void should_transition_to_failed_from_pending() {
        Transaction tx = createTestTransaction();
        tx.markPending();
        assertDoesNotThrow(tx::markFailed);
        assertEquals(TransactionState.FAILED, tx.snapshot().state());
    }

    @Test
    void should_record_historic_state_on_each_transition() {
        Transaction tx = createTestTransaction();
        tx.markPending();
        tx.markCompleted();
        // INITIALIZED (creation) + PENDING + COMPLETED = 3
        assertEquals(3, tx.snapshot().history().size());
    }

    @Test
    void should_throw_when_transition_is_invalid() {
        Transaction tx = createTestTransaction();
        // INITIALIZED → COMPLETED is invalid (must go through PENDING)
        assertThrows(InvalidStateTransitionException.class,
                tx::markCompleted);
    }

    @Test
    void should_throw_when_transitioning_from_terminal_state() {
        Transaction tx = createTestTransaction();
        tx.markPending();
        tx.markCompleted();
        // COMPLETED is terminal — any further transition is invalid
        assertThrows(InvalidStateTransitionException.class,
                tx::markFailed);
    }

    // ── Validation construction ───────────────────────────────────────────────

    @Test
    void should_throw_when_transaction_id_is_null() {
        assertThrows(IllegalArgumentException.class, () ->
                Transaction.create(null, "TRX-001", new Id("from"), new Id("to"),
                        TransactionType.CASH_IN, "MOBILE", AMT));
    }

    @Test
    void should_throw_when_from_id_is_null() {
        assertThrows(IllegalArgumentException.class, () ->
                Transaction.create(Id.generate(), "TRX-001", null, new Id("to"),
                        TransactionType.CASH_IN, "MOBILE", AMT));
    }

    @Test
    void should_throw_when_to_id_is_null() {
        assertThrows(IllegalArgumentException.class, () ->
                Transaction.create(Id.generate(), "TRX-001", new Id("from"), null,
                        TransactionType.CASH_IN, "MOBILE", AMT));
    }

    @Test
    void should_throw_when_amount_is_null() {
        assertThrows(IllegalArgumentException.class, () ->
                Transaction.create(Id.generate(), "TRX-001", new Id("from"), new Id("to"),
                        TransactionType.CASH_IN, "MOBILE", null));
    }
}