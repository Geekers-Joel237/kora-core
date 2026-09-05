package com.geekersjoel237.koracore.domain.model;

import com.geekersjoel237.koracore.domain.enums.LedgerEntryType;
import com.geekersjoel237.koracore.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.domain.enums.TransactionType;
import com.geekersjoel237.koracore.domain.exception.InvalidStateTransitionException;
import com.geekersjoel237.koracore.domain.exception.SelfTransferException;
import com.geekersjoel237.koracore.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {

    private static final Amount AMT = Amount.of(BigDecimal.valueOf(100), "XAF");

    private Transaction createTestTransaction() {
        return Transaction.create(
                Id.generate(),
                new Id("from-001"), new Id("to-001"),
                TransactionType.CASH_IN, PaymentMethod.MOBILE_MONEY, AMT
        );
    }

    private LedgerEntry testOperation() {
        return LedgerEntry.create(Id.generate(), LedgerEntryType.DEBIT, AMT, new Id("acc-001"));
    }

    // ── Construction ──────────────────────────────────────────────────────────

    @Test
    void should_create_transaction_with_initialized_state() {
        assertEquals(TransactionState.INITIALIZED, createTestTransaction().snapshot().state());
    }

    @Test
    void should_create_transaction_with_empty_operations() {
        assertTrue(createTestTransaction().entries().isEmpty());
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
                Id.generate(),
                new Id("from-001"), new Id("to-001"),
                TransactionType.CASH_IN, PaymentMethod.MOBILE_MONEY, AMT);
        assertEquals(new Id("from-001"), tx.snapshot().fromAccountId());
        assertEquals(new Id("to-001"), tx.snapshot().toAccountId());
    }

    // ── SelfTransfer invariant ────────────────────────────────────────────────

    @Test
    void should_throw_self_transfer_exception_when_from_and_to_are_identical() {
        Id sameId = Id.generate();
        assertThrows(SelfTransferException.class, () ->
                Transaction.create(Id.generate(), sameId, sameId,
                        TransactionType.P2P_TRANSFER, PaymentMethod.MOBILE_MONEY, AMT));
    }

    @Test
    void should_return_unmodifiable_operations_list() {
        Transaction tx = createTestTransaction();
        assertThrows(UnsupportedOperationException.class,
                () -> tx.entries().add(testOperation()));
    }

    // ── recordDoubleEntry ─────────────────────────────────────────────────────

    @Test
    void should_add_two_operations_on_record_double_entry() {
        Transaction tx = createTestTransaction();
        tx.recordDoubleEntry(AMT, new Id("debit-acc"), new Id("credit-acc"));
        assertEquals(2, tx.entries().size());
    }

    @Test
    void should_have_one_debit_and_one_credit_after_record_double_entry() {
        Transaction tx = createTestTransaction();
        tx.recordDoubleEntry(AMT, new Id("debit-acc"), new Id("credit-acc"));

        long debits  = tx.entries().stream()
                .filter(op -> op.snapshot().type() == LedgerEntryType.DEBIT).count();
        long credits = tx.entries().stream()
                .filter(op -> op.snapshot().type() == LedgerEntryType.CREDIT).count();

        assertEquals(1, debits);
        assertEquals(1, credits);
    }

    @Test
    void should_not_throw_when_double_entry_is_balanced() {
        Transaction tx = createTestTransaction();
        assertDoesNotThrow(() ->
                tx.recordDoubleEntry(AMT, new Id("from"), new Id("to")));
    }

    // ── transitionTo ──────────────────────────────────────────────────────────

    @Test
    void should_transition_to_authorized_from_initialized() {
        Transaction tx = createTestTransaction();
        assertDoesNotThrow(tx::authorize);
        assertEquals(TransactionState.AUTHORIZED, tx.snapshot().state());
    }

    @Test
    void should_transition_to_completed_through_full_lifecycle() {
        Transaction tx = createTestTransaction();
        tx.authorize();
        tx.capture();
        tx.pendSettlement();
        tx.settle();
        assertDoesNotThrow(tx::markCompleted);
        assertEquals(TransactionState.COMPLETED, tx.snapshot().state());
    }

    @Test
    void should_transition_to_failed_from_authorized() {
        Transaction tx = createTestTransaction();
        tx.authorize();
        assertDoesNotThrow(tx::markFailed);
        assertEquals(TransactionState.FAILED, tx.snapshot().state());
    }

    @Test
    void should_record_historic_state_on_each_transition() {
        Transaction tx = createTestTransaction();
        tx.authorize();
        tx.capture();
        tx.pendSettlement();
        tx.settle();
        tx.markCompleted();
        assertFalse(tx.snapshot().history().isEmpty());
        assertEquals(TransactionState.COMPLETED, tx.snapshot().history().getLast().newState());
    }

    @Test
    void should_throw_when_transition_is_invalid() {
        Transaction tx = createTestTransaction();
        // INITIALIZED → COMPLETED is invalid (must go through the full lifecycle)
        assertThrows(InvalidStateTransitionException.class,
                tx::markCompleted);
    }

    @Test
    void should_throw_when_transitioning_from_terminal_state() {
        Transaction tx = createTestTransaction();
        tx.authorize();
        tx.capture();
        tx.pendSettlement();
        tx.settle();
        tx.markCompleted();
        // COMPLETED is terminal — any further transition is invalid
        assertThrows(InvalidStateTransitionException.class,
                tx::markFailed);
    }

    // ── New terminal failure states ───────────────────────────────────────────

    @Test
    void should_transition_to_authorization_failed_from_authorized() {
        Transaction tx = createTestTransaction();
        tx.authorize();
        assertDoesNotThrow(tx::failAuthorization);
        assertEquals(TransactionState.AUTHORIZATION_FAILED, tx.snapshot().state());
        assertTrue(tx.snapshot().state().isTerminal());
    }

    @Test
    void should_transition_to_capture_failed_from_captured() {
        Transaction tx = createTestTransaction();
        tx.authorize();
        tx.capture();
        assertDoesNotThrow(tx::failCapture);
        assertEquals(TransactionState.CAPTURE_FAILED, tx.snapshot().state());
        assertTrue(tx.snapshot().state().isTerminal());
    }

    @Test
    void should_transition_to_settlement_failed_from_settlement_pending() {
        Transaction tx = createTestTransaction();
        tx.authorize();
        tx.capture();
        tx.pendSettlement();
        assertDoesNotThrow(tx::failSettlement);
        assertEquals(TransactionState.SETTLEMENT_FAILED, tx.snapshot().state());
        assertTrue(tx.snapshot().state().isTerminal());
    }

    @Test
    void should_transition_to_reversed_from_authorized() {
        Transaction tx = createTestTransaction();
        tx.authorize();
        assertDoesNotThrow(tx::reverse);
        assertEquals(TransactionState.REVERSED, tx.snapshot().state());
        assertTrue(tx.snapshot().state().isTerminal());
    }

    @Test
    void should_transition_to_reversed_from_captured() {
        Transaction tx = createTestTransaction();
        tx.authorize();
        tx.capture();
        assertDoesNotThrow(tx::reverse);
        assertEquals(TransactionState.REVERSED, tx.snapshot().state());
    }

    @Test
    void should_transition_to_reversed_from_settled() {
        Transaction tx = createTestTransaction();
        tx.authorize();
        tx.capture();
        tx.pendSettlement();
        tx.settle();
        assertDoesNotThrow(tx::reverse);
        assertEquals(TransactionState.REVERSED, tx.snapshot().state());
    }

    @Test
    void should_transition_to_reversed_from_completed() {
        Transaction tx = createTestTransaction();
        tx.authorize();
        tx.capture();
        tx.pendSettlement();
        tx.settle();
        tx.markCompleted();
        assertDoesNotThrow(tx::reverse);
        assertEquals(TransactionState.REVERSED, tx.snapshot().state());
    }

    @Test
    void should_throw_when_reversing_already_reversed_transaction() {
        Transaction tx = createTestTransaction();
        tx.authorize();
        tx.reverse();
        assertThrows(InvalidStateTransitionException.class, tx::reverse);
    }

    @Test
    void should_record_history_on_failure_transition() {
        Transaction tx = createTestTransaction();
        tx.authorize();
        tx.failAuthorization();
        var history = tx.snapshot().history();
        assertEquals("AUTHORIZED", history.getLast().snapshot().oldState());
        assertEquals("AUTHORIZATION_FAILED", history.getLast().snapshot().newState());
    }

    // ── Validation construction ───────────────────────────────────────────────

    @Test
    void should_throw_when_transaction_id_is_null() {
        assertThrows(IllegalArgumentException.class, () ->
                Transaction.create(null, new Id("from"), new Id("to"),
                        TransactionType.CASH_IN, PaymentMethod.MOBILE_MONEY, AMT));
    }

    @Test
    void should_throw_when_from_id_is_null() {
        assertThrows(IllegalArgumentException.class, () ->
                Transaction.create(Id.generate(), null, new Id("to"),
                        TransactionType.CASH_IN, PaymentMethod.MOBILE_MONEY, AMT));
    }

    @Test
    void should_throw_when_to_id_is_null() {
        assertThrows(IllegalArgumentException.class, () ->
                Transaction.create(Id.generate(), new Id("from"), null,
                        TransactionType.CASH_IN, PaymentMethod.MOBILE_MONEY, AMT));
    }

    @Test
    void should_throw_when_amount_is_null() {
        assertThrows(IllegalArgumentException.class, () ->
                Transaction.create(Id.generate(), new Id("from"), new Id("to"),
                        TransactionType.CASH_IN, PaymentMethod.MOBILE_MONEY, null));
    }
}