package com.geekersjoel237.koracore.payment.unit.domain.ledger;

import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.domain.model.Ledger;
import com.geekersjoel237.koracore.payment.domain.model.Transaction;
import com.geekersjoel237.koracore.payment.domain.enums.LedgerEntryType;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.shared.domain.exception.CurrencyMismatchException;
import com.geekersjoel237.koracore.payment.domain.exception.InsufficientFundsException;
import com.geekersjoel237.koracore.payment.domain.exception.InvalidAccountException;
import com.geekersjoel237.koracore.payment.domain.exception.SelfTransferException;
import com.geekersjoel237.koracore.payment.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerTest {

    private static final Amount HUNDRED = Amount.of(BigDecimal.valueOf(100), "XAF");

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
        accountA.credit(Amount.of(BigDecimal.valueOf(200), "XAF"));
        accountB        = Account.createCustomerAccount(Id.generate(), new Id("cust-B"));
    }

    private Id idOf(Account account) {
        return account.snapshot().accountId();
    }

    private List<Id> accountsOf(Transaction tx, LedgerEntryType type) {
        return tx.entries().stream()
                .filter(op -> op.snapshot().type() == type)
                .map(op -> op.snapshot().accountId())
                .toList();
    }

    private Amount sumByType(Transaction tx, LedgerEntryType type) {
        return tx.entries().stream()
                .filter(op -> op.snapshot().type() == type)
                .map(op -> op.snapshot().amount())
                .reduce(Amount.of(BigDecimal.ZERO, "XAF"), Amount::add);
    }

    private boolean isDoubleEntryBalanced(Transaction tx) {
        return sumByType(tx, LedgerEntryType.DEBIT).value()
                .compareTo(sumByType(tx, LedgerEntryType.CREDIT).value()) == 0;
    }

    private Transaction initiateTransfer() {
        return ledger.initiate(accountA, accountB,
                TransactionType.P2P_TRANSFER, PaymentMethod.WALLET, HUNDRED);
    }

    @Test
    void should_set_the_requested_transaction_type() {
        Transaction tx = ledger.initiate(floatAccount, customerAccount,
                TransactionType.CASH_IN, PaymentMethod.MOBILE_MONEY, HUNDRED);
        assertEquals(TransactionType.CASH_IN, tx.snapshot().type());
    }

    @Test
    void should_set_from_and_to_ids_from_the_accounts() {
        Transaction tx = ledger.initiate(floatAccount, customerAccount,
                TransactionType.CASH_IN, PaymentMethod.MOBILE_MONEY, HUNDRED);
        assertEquals(idOf(floatAccount), tx.snapshot().fromAccountId());
        assertEquals(idOf(customerAccount), tx.snapshot().toAccountId());
    }

    @Test
    void should_start_in_initialized_state() {
        assertEquals(TransactionState.INITIALIZED, initiateTransfer().snapshot().state());
    }

    @Test
    void should_generate_transaction_number_with_correct_format() {
        assertTrue(initiateTransfer().snapshot().transactionNumber()
                .matches("TRX-\\d{8}-[A-Z0-9]{8}"));
    }

    /**
     * The two-phase contract itself: a shell carries no ledger entry. Operations
     * appear only once the provider has captured, so a transaction that dies
     * between TX-1 and the provider call leaves no money movement behind.
     */
    @Test
    void should_record_no_operation_at_initiate() {
        assertTrue(initiateTransfer().entries().isEmpty());
    }

    @Test
    void should_throw_when_source_account_is_blocked() {
        Account blocked = Account.createCustomerAccount(Id.generate(), new Id("b-001"));
        blocked.block();
        assertThrows(InvalidAccountException.class,
                () -> ledger.initiate(blocked, accountB,
                        TransactionType.P2P_TRANSFER, PaymentMethod.WALLET, HUNDRED));
    }

    @Test
    void should_throw_when_customer_account_blocked_on_cash_in() {
        Account blocked = Account.createCustomerAccount(Id.generate(), new Id("b-002"));
        blocked.block();
        assertThrows(InvalidAccountException.class,
                () -> ledger.initiate(floatAccount, blocked,
                        TransactionType.CASH_IN, PaymentMethod.MOBILE_MONEY, HUNDRED));
    }

    @Test
    void should_throw_when_amount_is_zero() {
        assertThrows(IllegalArgumentException.class,
                () -> ledger.initiate(accountA, accountB,
                        TransactionType.P2P_TRANSFER, PaymentMethod.WALLET,
                        Amount.of(BigDecimal.ZERO, "XAF")));
    }

    @Test
    void should_throw_when_source_customer_account_has_insufficient_funds() {
        assertThrows(InsufficientFundsException.class,
                () -> ledger.initiate(accountA, accountB,
                        TransactionType.P2P_TRANSFER, PaymentMethod.WALLET,
                        Amount.of(BigDecimal.valueOf(201), "XAF")));
    }

    @Test
    void should_allow_initiate_with_the_exact_balance() {
        assertDoesNotThrow(() -> ledger.initiate(accountA, accountB,
                TransactionType.P2P_TRANSFER, PaymentMethod.WALLET,
                Amount.of(BigDecimal.valueOf(200), "XAF")));
    }

    /**
     * ADR-001: the float account is unbounded. Its balance_amount stays at zero
     * because Account.debit() is a no-op for a FLOAT_ACCOUNT, so a funds check
     * against it would reject every cash-in.
     */
    @Test
    void should_not_check_funds_when_the_source_is_the_float_account() {
        assertDoesNotThrow(() -> ledger.initiate(floatAccount, customerAccount,
                TransactionType.CASH_IN, PaymentMethod.MOBILE_MONEY,
                Amount.of(BigDecimal.valueOf(1_000_000), "XAF")));
    }

    @Test
    void should_throw_currency_mismatch_when_amount_currency_differs_from_the_balance() {
        // accountA holds XAF; the funds check compares two Amounts and refuses
        // to compare across currencies.
        assertThrows(CurrencyMismatchException.class,
                () -> ledger.initiate(accountA, accountB,
                        TransactionType.P2P_TRANSFER, PaymentMethod.WALLET,
                        Amount.of(BigDecimal.valueOf(100), "EUR")));
    }

    @Test
    void should_check_funds_before_building_the_transaction() {
        assertThrows(InsufficientFundsException.class,
                () -> ledger.initiate(accountB, accountB,
                        TransactionType.P2P_TRANSFER, PaymentMethod.WALLET, HUNDRED));
    }

    @Test
    void should_refuse_self_transfer_when_funds_cover_it() {
        assertThrows(SelfTransferException.class,
                () -> ledger.initiate(accountA, accountA,
                        TransactionType.P2P_TRANSFER, PaymentMethod.WALLET, HUNDRED));
    }

    @Test
    void should_produce_exactly_two_operations_on_write_entries() {
        Transaction tx = initiateTransfer();
        ledger.writeEntries(tx, accountA, accountB, HUNDRED);
        assertEquals(2, tx.entries().size());
    }

    @Test
    void should_debit_the_source_and_credit_the_destination() {
        Transaction tx = initiateTransfer();
        ledger.writeEntries(tx, accountA, accountB, HUNDRED);

        assertEquals(List.of(idOf(accountA)), accountsOf(tx, LedgerEntryType.DEBIT));
        assertEquals(List.of(idOf(accountB)), accountsOf(tx, LedgerEntryType.CREDIT));
    }

    @Test
    void should_maintain_double_entry_on_write_entries() {
        Transaction tx = initiateTransfer();
        ledger.writeEntries(tx, accountA, accountB, HUNDRED);
        assertTrue(isDoubleEntryBalanced(tx));
    }

    @Test
    void should_write_the_mirrored_pair_on_reverse() {
        Transaction tx = initiateTransfer();
        ledger.writeEntries(tx, accountA, accountB, HUNDRED);
        ledger.reverse(tx);

        assertEquals(List.of(idOf(accountA), idOf(accountB)), accountsOf(tx, LedgerEntryType.DEBIT));
        assertEquals(List.of(idOf(accountB), idOf(accountA)), accountsOf(tx, LedgerEntryType.CREDIT));
    }

    @Test
    void should_append_the_compensating_pair_without_removing_the_originals() {
        Transaction tx = initiateTransfer();
        ledger.writeEntries(tx, accountA, accountB, HUNDRED);
        ledger.reverse(tx);
        assertEquals(4, tx.entries().size());
    }

    @Test
    void should_keep_double_entry_balanced_after_reverse() {
        Transaction tx = initiateTransfer();
        ledger.writeEntries(tx, accountA, accountB, HUNDRED);
        ledger.reverse(tx);
        assertTrue(isDoubleEntryBalanced(tx));
    }

    @Test
    void should_reverse_the_transaction_amount() {
        Transaction tx = initiateTransfer();
        ledger.writeEntries(tx, accountA, accountB, HUNDRED);
        ledger.reverse(tx);

        // 100 written, 100 compensated → 200 on each side.
        assertEquals(0, sumByType(tx, LedgerEntryType.DEBIT).value()
                .compareTo(BigDecimal.valueOf(200)));
        assertEquals(0, sumByType(tx, LedgerEntryType.CREDIT).value()
                .compareTo(BigDecimal.valueOf(200)));
    }
}
