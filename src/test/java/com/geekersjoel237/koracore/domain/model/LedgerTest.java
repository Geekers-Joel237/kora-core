package com.geekersjoel237.koracore.domain.model;

import com.geekersjoel237.koracore.domain.enums.OperationType;
import com.geekersjoel237.koracore.domain.enums.TransactionType;
import com.geekersjoel237.koracore.domain.exception.CurrencyMismatchException;
import com.geekersjoel237.koracore.domain.exception.InsufficientFundsException;
import com.geekersjoel237.koracore.domain.exception.InvalidAccountException;
import com.geekersjoel237.koracore.domain.exception.SelfTransferException;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LedgerTest {

    private Ledger ledger;
    private Account customerAccount;
    private Account floatAccount;
    private Account accountA;
    private Account accountB;

    @BeforeEach
    void setUp() {
        ledger          = Ledger.create(Id.generate());
        customerAccount = Account.createCustomerAccount(Id.generate(), new Id("cust-001"));
        floatAccount    = Account.createFloatAccount(Id.generate(), new Id("prov-001"));
        accountA        = Account.createCustomerAccount(Id.generate(), new Id("cust-A"));
        accountA.credit(Amount.of(BigDecimal.valueOf(200), "XOF"));
        accountB        = Account.createCustomerAccount(Id.generate(), new Id("cust-B"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Amount sumByType(Transaction tx, OperationType type) {
        return tx.operations().stream()
                .filter(op -> op.snapshot().type() == type)
                .map(op -> op.snapshot().amount())
                .reduce(Amount.of(BigDecimal.ZERO, "XOF"), (a, b) -> a.add(b));
    }

    private boolean isDoubleEntryBalanced(Transaction tx) {
        Amount debit  = sumByType(tx, OperationType.DEBIT);
        Amount credit = sumByType(tx, OperationType.CREDIT);
        return debit.value().compareTo(credit.value()) == 0;
    }

    // ── cashIn ────────────────────────────────────────────────────────────────

    @Test
    void should_create_cash_in_transaction_with_correct_type() {
        Transaction tx = ledger.cashIn(customerAccount, floatAccount,
                Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE");
        assertEquals(TransactionType.CASH_IN, tx.snapshot().type());
    }

    @Test
    void should_produce_exactly_two_operations_on_cash_in() {
        Transaction tx = ledger.cashIn(customerAccount, floatAccount,
                Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE");
        assertEquals(2, tx.operations().size());
    }

    @Test
    void should_debit_float_account_on_cash_in() {
        Transaction tx = ledger.cashIn(customerAccount, floatAccount,
                Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE");
        assertEquals(floatAccount.snapshot().accountId(),
                tx.operations().get(0).snapshot().accountId());
        assertEquals(OperationType.DEBIT, tx.operations().get(0).snapshot().type());
    }

    @Test
    void should_credit_customer_account_on_cash_in() {
        Transaction tx = ledger.cashIn(customerAccount, floatAccount,
                Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE");
        assertEquals(customerAccount.snapshot().accountId(),
                tx.operations().get(1).snapshot().accountId());
        assertEquals(OperationType.CREDIT, tx.operations().get(1).snapshot().type());
    }

    @Test
    void should_set_from_id_to_float_on_cash_in() {
        Transaction tx = ledger.cashIn(customerAccount, floatAccount,
                Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE");
        assertEquals(floatAccount.snapshot().accountId(), tx.snapshot().fromId());
    }

    @Test
    void should_set_to_id_to_customer_on_cash_in() {
        Transaction tx = ledger.cashIn(customerAccount, floatAccount,
                Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE");
        assertEquals(customerAccount.snapshot().accountId(), tx.snapshot().toId());
    }

    @Test
    void should_maintain_double_entry_on_cash_in() {
        Transaction tx = ledger.cashIn(customerAccount, floatAccount,
                Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE");
        assertTrue(isDoubleEntryBalanced(tx));
    }

    @Test
    void should_generate_transaction_number_with_correct_format() {
        Transaction tx = ledger.cashIn(customerAccount, floatAccount,
                Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE");
        assertTrue(tx.snapshot().transactionNumber().matches("TRX-\\d{8}-[A-Z0-9]{4}"));
    }

    @Test
    void should_throw_when_customer_account_blocked_on_cash_in() {
        Account blocked = Account.createCustomerAccount(Id.generate(), new Id("b-001"));
        blocked.block();
        assertThrows(InvalidAccountException.class,
                () -> ledger.cashIn(blocked, floatAccount,
                        Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE"));
    }

    @Test
    void should_throw_when_float_account_blocked_on_cash_in() {
        Account blockedFloat = Account.createFloatAccount(Id.generate(), new Id("b-prov"));
        blockedFloat.block();
        assertThrows(InvalidAccountException.class,
                () -> ledger.cashIn(customerAccount, blockedFloat,
                        Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE"));
    }

    @Test
    void should_throw_when_amount_is_zero_on_cash_in() {
        assertThrows(IllegalArgumentException.class,
                () -> ledger.cashIn(customerAccount, floatAccount,
                        Amount.of(BigDecimal.ZERO, "XOF"), "MOBILE"));
    }

    // ── cashOut ───────────────────────────────────────────────────────────────

    @Test
    void should_create_cash_out_transaction_with_correct_type() {
        Transaction tx = ledger.cashOut(accountA, floatAccount,
                Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE");
        assertEquals(TransactionType.CASH_OUT, tx.snapshot().type());
    }

    @Test
    void should_produce_exactly_two_operations_on_cash_out() {
        Transaction tx = ledger.cashOut(accountA, floatAccount,
                Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE");
        assertEquals(2, tx.operations().size());
    }

    @Test
    void should_debit_customer_account_on_cash_out() {
        Transaction tx = ledger.cashOut(accountA, floatAccount,
                Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE");
        assertEquals(accountA.snapshot().accountId(),
                tx.operations().get(0).snapshot().accountId());
        assertEquals(OperationType.DEBIT, tx.operations().get(0).snapshot().type());
    }

    @Test
    void should_credit_float_account_on_cash_out() {
        Transaction tx = ledger.cashOut(accountA, floatAccount,
                Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE");
        assertEquals(floatAccount.snapshot().accountId(),
                tx.operations().get(1).snapshot().accountId());
        assertEquals(OperationType.CREDIT, tx.operations().get(1).snapshot().type());
    }

    @Test
    void should_maintain_double_entry_on_cash_out() {
        Transaction tx = ledger.cashOut(accountA, floatAccount,
                Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE");
        assertTrue(isDoubleEntryBalanced(tx));
    }

    @Test
    void should_allow_cash_out_with_exact_balance() {
        assertDoesNotThrow(() -> ledger.cashOut(accountA, floatAccount,
                Amount.of(BigDecimal.valueOf(200), "XOF"), "MOBILE"));
    }

    @Test
    void should_throw_when_insufficient_funds_on_cash_out() {
        assertThrows(InsufficientFundsException.class,
                () -> ledger.cashOut(accountA, floatAccount,
                        Amount.of(BigDecimal.valueOf(300), "XOF"), "MOBILE"));
    }

    @Test
    void should_throw_when_account_blocked_on_cash_out() {
        Account blocked = Account.createCustomerAccount(Id.generate(), new Id("b-002"));
        blocked.credit(Amount.of(BigDecimal.valueOf(200), "XOF"));
        blocked.block();
        assertThrows(InvalidAccountException.class,
                () -> ledger.cashOut(blocked, floatAccount,
                        Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE"));
    }

    // ── transfer ──────────────────────────────────────────────────────────────

    @Test
    void should_create_p2p_transfer_transaction_with_correct_type() {
        Transaction tx = ledger.transfer(accountA, accountB,
                Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE");
        assertEquals(TransactionType.P2P_TRANSFER, tx.snapshot().type());
    }

    @Test
    void should_produce_exactly_two_operations_on_transfer() {
        Transaction tx = ledger.transfer(accountA, accountB,
                Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE");
        assertEquals(2, tx.operations().size());
    }

    @Test
    void should_debit_sender_on_transfer() {
        Transaction tx = ledger.transfer(accountA, accountB,
                Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE");
        assertEquals(accountA.snapshot().accountId(),
                tx.operations().get(0).snapshot().accountId());
        assertEquals(OperationType.DEBIT, tx.operations().get(0).snapshot().type());
    }

    @Test
    void should_credit_receiver_on_transfer() {
        Transaction tx = ledger.transfer(accountA, accountB,
                Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE");
        assertEquals(accountB.snapshot().accountId(),
                tx.operations().get(1).snapshot().accountId());
        assertEquals(OperationType.CREDIT, tx.operations().get(1).snapshot().type());
    }

    @Test
    void should_maintain_double_entry_on_transfer() {
        Transaction tx = ledger.transfer(accountA, accountB,
                Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE");
        assertTrue(isDoubleEntryBalanced(tx));
    }

    @Test
    void should_throw_when_self_transfer() {
        assertThrows(SelfTransferException.class,
                () -> ledger.transfer(accountA, accountA,
                        Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE"));
    }

    @Test
    void should_throw_when_insufficient_funds_on_transfer() {
        assertThrows(InsufficientFundsException.class,
                () -> ledger.transfer(accountA, accountB,
                        Amount.of(BigDecimal.valueOf(300), "XOF"), "MOBILE"));
    }

    @Test
    void should_throw_when_receiver_blocked_on_transfer() {
        accountB.block();
        assertThrows(InvalidAccountException.class,
                () -> ledger.transfer(accountA, accountB,
                        Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE"));
    }

    @Test
    void should_throw_when_sender_blocked_on_transfer() {
        Account blockedSender = Account.createCustomerAccount(Id.generate(), new Id("b-sender"));
        blockedSender.block();
        assertThrows(InvalidAccountException.class,
                () -> ledger.transfer(blockedSender, accountB,
                        Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE"));
    }

    @Test
    void should_throw_when_currency_mismatch_on_transfer() {
        // accountA balance is XOF; trying to transfer EUR triggers CurrencyMismatchException
        // during the isGreaterThanOrEqual balance check
        assertThrows(CurrencyMismatchException.class,
                () -> ledger.transfer(accountA, accountB,
                        Amount.of(BigDecimal.valueOf(100), "EUR"), "MOBILE"));
    }

    // ── Invariants transversaux ───────────────────────────────────────────────

    @Test
    void should_always_produce_exactly_two_operations_across_all_types() {
        Transaction cashIn = ledger.cashIn(customerAccount, floatAccount,
                Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE");
        Transaction cashOut = ledger.cashOut(accountA, floatAccount,
                Amount.of(BigDecimal.valueOf(50), "XOF"), "MOBILE");
        Transaction transfer = ledger.transfer(accountA, accountB,
                Amount.of(BigDecimal.valueOf(50), "XOF"), "MOBILE");

        assertEquals(2, cashIn.operations().size());
        assertEquals(2, cashOut.operations().size());
        assertEquals(2, transfer.operations().size());
    }

    @Test
    void should_always_maintain_double_entry_across_all_types() {
        Transaction cashIn = ledger.cashIn(customerAccount, floatAccount,
                Amount.of(BigDecimal.valueOf(100), "XOF"), "MOBILE");
        Transaction cashOut = ledger.cashOut(accountA, floatAccount,
                Amount.of(BigDecimal.valueOf(50), "XOF"), "MOBILE");
        Transaction transfer = ledger.transfer(accountA, accountB,
                Amount.of(BigDecimal.valueOf(50), "XOF"), "MOBILE");

        assertTrue(isDoubleEntryBalanced(cashIn));
        assertTrue(isDoubleEntryBalanced(cashOut));
        assertTrue(isDoubleEntryBalanced(transfer));
    }
}