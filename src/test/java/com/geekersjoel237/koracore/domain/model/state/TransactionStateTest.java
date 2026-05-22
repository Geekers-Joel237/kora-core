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
    void should_allow_transition_from_initialized_to_failed() {
        assertDoesNotThrow(() -> TransactionState.INITIALIZED.transitionTo(TransactionState.FAILED));
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
    void should_allow_transition_from_authorized_to_authorization_failed() {
        assertDoesNotThrow(() -> TransactionState.AUTHORIZED.transitionTo(TransactionState.AUTHORIZATION_FAILED));
    }

    @Test
    void should_allow_transition_from_authorized_to_reversed() {
        assertDoesNotThrow(() -> TransactionState.AUTHORIZED.transitionTo(TransactionState.REVERSED));
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
    void should_allow_transition_from_captured_to_capture_failed() {
        assertDoesNotThrow(() -> TransactionState.CAPTURED.transitionTo(TransactionState.CAPTURE_FAILED));
    }

    @Test
    void should_allow_transition_from_captured_to_reversed() {
        assertDoesNotThrow(() -> TransactionState.CAPTURED.transitionTo(TransactionState.REVERSED));
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
    void should_allow_transition_from_settlement_pending_to_settlement_failed() {
        assertDoesNotThrow(() -> TransactionState.SETTLEMENT_PENDING.transitionTo(TransactionState.SETTLEMENT_FAILED));
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

    // ── AuthorizationFailedState (terminal) ───────────────────────────────────

    @Test
    void should_throw_when_transitioning_from_authorization_failed_to_authorized() {
        assertThrows(InvalidStateTransitionException.class,
                () -> TransactionState.AUTHORIZATION_FAILED.transitionTo(TransactionState.AUTHORIZED));
    }

    // ── CaptureFailedState (terminal) ─────────────────────────────────────────

    @Test
    void should_throw_when_transitioning_from_capture_failed_to_captured() {
        assertThrows(InvalidStateTransitionException.class,
                () -> TransactionState.CAPTURE_FAILED.transitionTo(TransactionState.CAPTURED));
    }

    // ── SettlementFailedState (terminal) ──────────────────────────────────────

    @Test
    void should_throw_when_transitioning_from_settlement_failed_to_settled() {
        assertThrows(InvalidStateTransitionException.class,
                () -> TransactionState.SETTLEMENT_FAILED.transitionTo(TransactionState.SETTLED));
    }

    // ── ReversedState (terminal) ──────────────────────────────────────────────

    @Test
    void should_throw_when_transitioning_from_reversed_to_authorized() {
        assertThrows(InvalidStateTransitionException.class,
                () -> TransactionState.REVERSED.transitionTo(TransactionState.AUTHORIZED));
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

    // ── isTerminal() ──────────────────────────────────────────────────────────

    @Test
    void non_terminal_states_should_return_false_for_isTerminal() {
        assertFalse(TransactionState.INITIALIZED.isTerminal());
        assertFalse(TransactionState.AUTHORIZED.isTerminal());
        assertFalse(TransactionState.CAPTURED.isTerminal());
        assertFalse(TransactionState.SETTLEMENT_PENDING.isTerminal());
        assertFalse(TransactionState.SETTLED.isTerminal());
    }

    @Test
    void terminal_states_should_return_true_for_isTerminal() {
        assertTrue(TransactionState.COMPLETED.isTerminal());
        assertTrue(TransactionState.FAILED.isTerminal());
        assertTrue(TransactionState.AUTHORIZATION_FAILED.isTerminal());
        assertTrue(TransactionState.CAPTURE_FAILED.isTerminal());
        assertTrue(TransactionState.SETTLEMENT_FAILED.isTerminal());
        assertTrue(TransactionState.REVERSED.isTerminal());
    }
}