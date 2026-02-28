package com.geekersjoel237.koracore.domain.model.state;

import com.geekersjoel237.koracore.domain.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransactionStateTest {

    // ── InitializedState ─────────────────────────────────────────────────────

    @Test
    void should_allow_transition_from_initialized_to_pending() {
        assertDoesNotThrow(() -> TransactionState.INITIALIZED.transitionTo(TransactionState.PENDING));
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

    // ── PendingState ─────────────────────────────────────────────────────────

    @Test
    void should_allow_transition_from_pending_to_completed() {
        assertDoesNotThrow(() -> TransactionState.PENDING.transitionTo(TransactionState.COMPLETED));
    }

    @Test
    void should_allow_transition_from_pending_to_failed() {
        assertDoesNotThrow(() -> TransactionState.PENDING.transitionTo(TransactionState.FAILED));
    }

    @Test
    void should_throw_when_transitioning_from_pending_to_initialized() {
        assertThrows(InvalidStateTransitionException.class,
                () -> TransactionState.PENDING.transitionTo(TransactionState.INITIALIZED));
    }

    // ── CompletedState (terminal) ─────────────────────────────────────────────

    @Test
    void should_throw_when_transitioning_from_completed_to_pending() {
        assertThrows(InvalidStateTransitionException.class,
                () -> TransactionState.COMPLETED.transitionTo(TransactionState.PENDING));
    }

    @Test
    void should_throw_when_transitioning_from_completed_to_failed() {
        assertThrows(InvalidStateTransitionException.class,
                () -> TransactionState.COMPLETED.transitionTo(TransactionState.FAILED));
    }

    // ── FailedState (terminal) ────────────────────────────────────────────────

    @Test
    void should_throw_when_transitioning_from_failed_to_pending() {
        assertThrows(InvalidStateTransitionException.class,
                () -> TransactionState.FAILED.transitionTo(TransactionState.PENDING));
    }

    @Test
    void should_throw_when_transitioning_from_failed_to_completed() {
        assertThrows(InvalidStateTransitionException.class,
                () -> TransactionState.FAILED.transitionTo(TransactionState.COMPLETED));
    }

    // ── Return value ─────────────────────────────────────────────────────────

    @Test
    void should_return_next_state_on_valid_transition() {
        TransactionState result = TransactionState.INITIALIZED.transitionTo(TransactionState.PENDING);
        assertEquals(TransactionState.PENDING, result);
    }

    @Test
    void should_return_completed_after_pending_transitions_to_completed() {
        TransactionState result = TransactionState.PENDING.transitionTo(TransactionState.COMPLETED);
        assertEquals(TransactionState.COMPLETED, result);
    }
}