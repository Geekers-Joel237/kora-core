package com.geekersjoel237.koracore.domain.model;

import com.geekersjoel237.koracore.domain.enums.TriggerSource;
import com.geekersjoel237.koracore.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.domain.vo.Id;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrxStateHistoricTest {

    private static final Id TX_ID = new Id("tx-001");

    @Test
    void three_arg_of_compiles_and_does_not_throw() {
        assertDoesNotThrow(() ->
                TrxStateHistoric.of(TX_ID, TransactionState.INITIALIZED, TransactionState.AUTHORIZED));
    }

    @Test
    void eight_arg_of_with_operator_action_and_non_blank_notes_does_not_throw() {
        assertDoesNotThrow(() ->
                TrxStateHistoric.of(
                        TX_ID,
                        TransactionState.AUTHORIZED, TransactionState.REVERSED,
                        TriggerSource.OPERATOR_ACTION,
                        new Id("corr-001"), "prov-ref-001", new Id("operator-42"),
                        "Reversal approved by compliance team"));
    }

    @Test
    void eight_arg_of_with_operator_action_and_null_notes_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                TrxStateHistoric.of(
                        TX_ID,
                        TransactionState.AUTHORIZED, TransactionState.REVERSED,
                        TriggerSource.OPERATOR_ACTION,
                        new Id("corr-001"), "prov-ref-001", new Id("operator-42"),
                        null));
    }

    @Test
    void eight_arg_of_with_operator_action_and_blank_notes_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                TrxStateHistoric.of(
                        TX_ID,
                        TransactionState.AUTHORIZED, TransactionState.REVERSED,
                        TriggerSource.OPERATOR_ACTION,
                        new Id("corr-001"), "prov-ref-001", new Id("operator-42"),
                        "   "));
    }

    @Test
    void eight_arg_of_with_user_action_and_null_notes_does_not_throw() {
        assertDoesNotThrow(() ->
                TrxStateHistoric.of(
                        TX_ID,
                        TransactionState.INITIALIZED, TransactionState.AUTHORIZED,
                        TriggerSource.USER_ACTION,
                        new Id("corr-002"), null, new Id("user-99"),
                        null));
    }

    @Test
    void snapshot_includes_all_new_fields() {
        TrxStateHistoric historic = TrxStateHistoric.of(
                TX_ID,
                TransactionState.CAPTURED, TransactionState.SETTLEMENT_PENDING,
                TriggerSource.PROVIDER_CALLBACK,
                new Id("corr-abc"), "prov-xyz", new Id("actor-1"),
                "Settlement triggered by callback");

        TrxStateHistoric.Snapshot snap = historic.snapshot();

        assertNotNull(snap.id());
        assertEquals(TX_ID.value(), snap.transactionId());
        assertEquals("CAPTURED",            snap.oldState());
        assertEquals("SETTLEMENT_PENDING",  snap.newState());
        assertNotNull(snap.occurredAt());
        assertEquals(TriggerSource.PROVIDER_CALLBACK, snap.triggeredBy());
        assertEquals("corr-abc",            snap.correlationId());
        assertEquals("prov-xyz",            snap.providerRef());
        assertEquals("actor-1",             snap.actorId());
        assertEquals("Settlement triggered by callback", snap.notes());
    }

    @Test
    void three_arg_snapshot_has_null_enriched_fields() {
        TrxStateHistoric historic = TrxStateHistoric.of(
                TX_ID, TransactionState.INITIALIZED, TransactionState.AUTHORIZED);

        TrxStateHistoric.Snapshot snap = historic.snapshot();

        assertNull(snap.triggeredBy());
        assertNull(snap.correlationId());
        assertNull(snap.providerRef());
        assertNull(snap.actorId());
        assertNull(snap.notes());
    }
}