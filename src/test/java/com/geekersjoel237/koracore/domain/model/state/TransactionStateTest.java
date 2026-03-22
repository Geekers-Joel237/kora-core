package com.geekersjoel237.koracore.domain.model.state;

import com.geekersjoel237.koracore.domain.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransactionStateTest {

    // ── InitializedState ─────────────────────────────────────────────────────

    @Test
    void should_allow_transition_from_initialized_to_authorized() {
        assertDoesNotThrow(() -> TransactionState.INITIALIZED.transitionTo(TransactionState.AUTHORIZED));
    }

    @Test
    void should_throw_when_transitioning_from_initialized_to_completed() {
        assertThrows(InvalidStateTransitionException.class,
                () -> TransactionState.INITIALIZED.transitionTo(TransactionState.COMPLETED));
    }

    @Test
    void should_throw_when_transitioning_from_initialized_to_failed() {
        assertThrows(InvalidStateTransitionException.class,
                () -> TransactionState.INITIALIZED.transitionTo(TransactionState.FAILED));
    }

    // ── AuthorizedState ───────────────────────────────────────────────────────

    @Test
    void should_allow_transition_from_authorized_to_captured() {
        assertDoesNotThrow(() -> TransactionState.AUTHORIZED.transitionTo(TransactionState.CAPTURED));
    }

    @Test
    void should_allow_transition_from_authorized_to_failed() {
        assertDoesNotThrow(() -> TransactionState.AUTHORIZED.transitionTo(TransactionState.FAILED));
    }

    @Test
    void should_throw_when_transitioning_from_authorized_to_completed() {
        assertThrows(InvalidStateTransitionException.class,
                () -> TransactionState.AUTHORIZED.transitionTo(TransactionState.COMPLETED));
    }

    // ── CapturedState ─────────────────────────────────────────────────────────

    @Test
    void should_allow_transition_from_captured_to_settlement_pending() {
        assertDoesNotThrow(() -> TransactionState.CAPTURED.transitionTo(TransactionState.SETTLEMENT_PENDING));
    }

    @Test
    void should_throw_when_transitioning_from_captured_to_completed() {
        assertThrows(InvalidStateTransitionException.class,
                () -> TransactionState.CAPTURED.transitionTo(TransactionState.COMPLETED));
    }

    // ── SettlementPendingState ────────────────────────────────────────────────

    @Test
    void should_allow_transition_from_settlement_pending_to_settled() {
        assertDoesNotThrow(() -> TransactionState.SETTLEMENT_PENDING.transitionTo(TransactionState.SETTLED));
    }

    @Test
    void should_throw_when_transitioning_from_settlement_pending_to_completed() {
        assertThrows(InvalidStateTransitionException.class,
                () -> TransactionState.SETTLEMENT_PENDING.transitionTo(TransactionState.COMPLETED));
    }

    // ── SettledState ──────────────────────────────────────────────────────────

    @Test
    void should_allow_transition_from_settled_to_completed() {
        assertDoesNotThrow(() -> TransactionState.SETTLED.transitionTo(TransactionState.COMPLETED));
    }

    @Test
    void should_throw_when_transitioning_from_settled_to_failed() {
        assertThrows(InvalidStateTransitionException.class,
                () -> TransactionState.SETTLED.transitionTo(TransactionState.FAILED));
    }

    // ── CompletedState (terminal) ─────────────────────────────────────────────

    @Test
    void should_throw_when_transitioning_from_completed_to_authorized() {
        assertThrows(InvalidStateTransitionException.class,
                () -> TransactionState.COMPLETED.transitionTo(TransactionState.AUTHORIZED));
    }

    @Test
    void should_throw_when_transitioning_from_completed_to_failed() {
        assertThrows(InvalidStateTransitionException.class,
                () -> TransactionState.COMPLETED.transitionTo(TransactionState.FAILED));
    }

    // ── FailedState (terminal) ────────────────────────────────────────────────

    @Test
    void should_throw_when_transitioning_from_failed_to_authorized() {
        assertThrows(InvalidStateTransitionException.class,
                () -> TransactionState.FAILED.transitionTo(TransactionState.AUTHORIZED));
    }

    @Test
    void should_throw_when_transitioning_from_failed_to_completed() {
        assertThrows(InvalidStateTransitionException.class,
                () -> TransactionState.FAILED.transitionTo(TransactionState.COMPLETED));
    }

    // ── Return value ─────────────────────────────────────────────────────────

    @Test
    void should_return_next_state_on_valid_transition() {
        TransactionState result = TransactionState.INITIALIZED.transitionTo(TransactionState.AUTHORIZED);
        assertEquals(TransactionState.AUTHORIZED, result);
    }

    @Test
    void should_return_completed_after_settled_transitions_to_completed() {
        TransactionState result = TransactionState.SETTLED.transitionTo(TransactionState.COMPLETED);
        assertEquals(TransactionState.COMPLETED, result);
    }
}