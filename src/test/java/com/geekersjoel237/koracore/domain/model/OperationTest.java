package com.geekersjoel237.koracore.domain.model;

import com.geekersjoel237.koracore.domain.enums.OperationType;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OperationTest {

    private static final Id OP_ID    = new Id("op-001");
    private static final Id ACC_ID   = new Id("acc-001");
    private static final Amount AMT  = Amount.of(BigDecimal.valueOf(100), "XOF");

    // ── Construction valide ───────────────────────────────────────────────────

    @Test
    void should_create_debit_operation_when_valid_params() {
        Operation op = Operation.create(OP_ID, OperationType.DEBIT, AMT, ACC_ID);
        assertEquals(OperationType.DEBIT, op.snapshot().type());
    }

    @Test
    void should_create_credit_operation_when_valid_params() {
        Amount creditAmt = Amount.of(BigDecimal.valueOf(50), "XOF");
        Operation op = Operation.create(OP_ID, OperationType.CREDIT, creditAmt, ACC_ID);
        assertEquals(creditAmt, op.snapshot().amount());
    }

    @Test
    void should_set_created_at_on_creation() {
        Operation op = Operation.create(OP_ID, OperationType.DEBIT, AMT, ACC_ID);
        assertNotNull(op.snapshot().createdAt());
    }

    @Test
    void should_store_account_id_correctly() {
        Operation op = Operation.create(OP_ID, OperationType.DEBIT, AMT, ACC_ID);
        assertEquals(ACC_ID, op.snapshot().accountId());
    }

    // ── Validation construction ───────────────────────────────────────────────

    @Test
    void should_throw_when_operation_id_is_null() {
        assertThrows(IllegalArgumentException.class,
                () -> Operation.create(null, OperationType.DEBIT, AMT, ACC_ID));
    }

    @Test
    void should_throw_when_type_is_null() {
        assertThrows(IllegalArgumentException.class,
                () -> Operation.create(OP_ID, null, AMT, ACC_ID));
    }

    @Test
    void should_throw_when_amount_is_null() {
        assertThrows(IllegalArgumentException.class,
                () -> Operation.create(OP_ID, OperationType.DEBIT, null, ACC_ID));
    }

    @Test
    void should_throw_when_account_id_is_null() {
        assertThrows(IllegalArgumentException.class,
                () -> Operation.create(OP_ID, OperationType.DEBIT, AMT, null));
    }
}