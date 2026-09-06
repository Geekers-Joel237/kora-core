package com.geekersjoel237.koracore.payment.unit.domain.ledger;

import com.geekersjoel237.koracore.payment.domain.model.LedgerEntry;
import com.geekersjoel237.koracore.payment.domain.enums.LedgerEntryType;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class LedgerEntryTest {

    private static final Id OP_ID    = new Id("op-001");
    private static final Id ACC_ID   = new Id("acc-001");
    private static final Amount AMT  = Amount.of(BigDecimal.valueOf(100), "XAF");

    // ── Construction valide ───────────────────────────────────────────────────

    @Test
    void should_create_debit_operation_when_valid_params() {
        LedgerEntry op = LedgerEntry.create(OP_ID, LedgerEntryType.DEBIT, AMT, ACC_ID);
        assertEquals(LedgerEntryType.DEBIT, op.snapshot().type());
    }

    @Test
    void should_create_credit_operation_when_valid_params() {
        Amount creditAmt = Amount.of(BigDecimal.valueOf(50), "XAF");
        LedgerEntry op = LedgerEntry.create(OP_ID, LedgerEntryType.CREDIT, creditAmt, ACC_ID);
        assertEquals(creditAmt, op.snapshot().amount());
    }

    @Test
    void should_set_created_at_on_creation() {
        LedgerEntry op = LedgerEntry.create(OP_ID, LedgerEntryType.DEBIT, AMT, ACC_ID);
        assertNotNull(op.snapshot().createdAt());
    }

    @Test
    void should_store_account_id_correctly() {
        LedgerEntry op = LedgerEntry.create(OP_ID, LedgerEntryType.DEBIT, AMT, ACC_ID);
        assertEquals(ACC_ID, op.snapshot().accountId());
    }

    // ── Validation construction ───────────────────────────────────────────────

    @Test
    void should_throw_when_operation_id_is_null() {
        assertThrows(IllegalArgumentException.class,
                () -> LedgerEntry.create(null, LedgerEntryType.DEBIT, AMT, ACC_ID));
    }

    @Test
    void should_throw_when_type_is_null() {
        assertThrows(IllegalArgumentException.class,
                () -> LedgerEntry.create(OP_ID, null, AMT, ACC_ID));
    }

    @Test
    void should_throw_when_amount_is_null() {
        assertThrows(IllegalArgumentException.class,
                () -> LedgerEntry.create(OP_ID, LedgerEntryType.DEBIT, null, ACC_ID));
    }

    @Test
    void should_throw_when_account_id_is_null() {
        assertThrows(IllegalArgumentException.class,
                () -> LedgerEntry.create(OP_ID, LedgerEntryType.DEBIT, AMT, null));
    }
}