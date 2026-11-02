package com.geekersjoel237.koracore.payment.unit.domain.ledger;

import com.geekersjoel237.koracore.payment.domain.enums.HoldStatus;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.domain.model.FundsHold;
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

    private static Amount xaf(long value) {
        return Amount.of(BigDecimal.valueOf(value), "XAF");
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

    private static void assertAmount(long expected, Amount actual) {
        assertEquals(0, actual.value().compareTo(BigDecimal.valueOf(expected)));
    }

    private Transaction initiateTransfer() {
        return ledger.initiate(accountA, accountB,
                TransactionType.P2P_TRANSFER, PaymentMethod.WALLET, HUNDRED);
    }

    private Transaction postedTransfer() {
        Transaction tx = initiateTransfer();
        ledger.post(tx, accountA, accountB);
        return tx;
    }

    private Transaction initiateCashOut() {
        return ledger.initiate(accountA, floatAccount,
                TransactionType.CASH_OUT, PaymentMethod.MOBILE_MONEY, HUNDRED);
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
                        TransactionType.P2P_TRANSFER, PaymentMethod.WALLET, xaf(0)));
    }

    @Test
    void should_throw_when_source_customer_account_has_insufficient_funds() {
        assertThrows(InsufficientFundsException.class,
                () -> ledger.initiate(accountA, accountB,
                        TransactionType.P2P_TRANSFER, PaymentMethod.WALLET, xaf(201)));
    }

    @Test
    void should_allow_initiate_with_the_exact_balance() {
        assertDoesNotThrow(() -> ledger.initiate(accountA, accountB,
                TransactionType.P2P_TRANSFER, PaymentMethod.WALLET, xaf(200)));
    }

    @Test
    void should_refuse_to_initiate_against_reserved_funds() {
        accountA.reserve(xaf(150));

        assertThrows(InsufficientFundsException.class, this::initiateTransfer);
    }

    /**
     * ADR-001: the float account is unbounded. Its balance_amount stays at zero
     * because Account.debit() is a no-op for a FLOAT_ACCOUNT, so a funds check
     * against it would reject every cash-in.
     */
    @Test
    void should_not_check_funds_when_the_source_is_the_float_account() {
        assertDoesNotThrow(() -> ledger.initiate(floatAccount, customerAccount,
                TransactionType.CASH_IN, PaymentMethod.MOBILE_MONEY, xaf(1_000_000)));
    }

    @Test
    void should_throw_currency_mismatch_when_amount_currency_differs_from_the_balance() {
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
    void should_record_exactly_two_entries_on_post() {
        assertEquals(2, postedTransfer().entries().size());
    }

    @Test
    void should_debit_the_source_and_credit_the_destination_on_post() {
        Transaction tx = postedTransfer();

        assertEquals(List.of(idOf(accountA)), accountsOf(tx, LedgerEntryType.DEBIT));
        assertEquals(List.of(idOf(accountB)), accountsOf(tx, LedgerEntryType.CREDIT));
        assertAmount(100, accountA.snapshot().balance().solde());
        assertAmount(100, accountB.snapshot().balance().solde());
    }

    @Test
    void should_maintain_double_entry_on_post() {
        assertTrue(isDoubleEntryBalanced(postedTransfer()));
    }

    @Test
    void should_refuse_to_post_against_reserved_funds() {
        Transaction tx = initiateTransfer();
        accountA.reserve(xaf(150));

        assertThrows(InsufficientFundsException.class, () -> ledger.post(tx, accountA, accountB));
        assertTrue(tx.entries().isEmpty());
    }

    @Test
    void should_refuse_to_post_between_accounts_that_are_not_the_parties() {
        Transaction tx = initiateTransfer();

        assertThrows(IllegalArgumentException.class, () -> ledger.post(tx, accountB, accountA));
    }

    @Test
    void should_hold_the_amount_on_the_source() {
        Transaction tx = initiateCashOut();

        FundsHold hold = ledger.hold(tx, accountA);

        assertEquals(HoldStatus.ACTIVE, hold.snapshot().status());
        assertEquals(tx.snapshot().transactionId(), hold.snapshot().transactionId());
        assertEquals(idOf(accountA), hold.snapshot().accountId());
        assertAmount(100, accountA.available());
        assertAmount(200, accountA.snapshot().balance().solde());
        assertTrue(tx.entries().isEmpty());
    }

    @Test
    void should_refuse_a_hold_the_available_balance_cannot_cover() {
        Transaction tx = initiateCashOut();
        accountA.reserve(xaf(150));

        assertThrows(InsufficientFundsException.class, () -> ledger.hold(tx, accountA));
    }

    @Test
    void should_refuse_a_hold_on_an_account_that_is_not_the_source() {
        Transaction tx = initiateCashOut();

        assertThrows(IllegalArgumentException.class, () -> ledger.hold(tx, accountB));
    }

    @Test
    void should_capture_a_hold_into_entries_and_a_debit() {
        Transaction tx = initiateCashOut();
        FundsHold hold = ledger.hold(tx, accountA);

        ledger.capture(tx, hold, accountA, floatAccount);

        assertEquals(HoldStatus.CONSUMED, hold.snapshot().status());
        assertAmount(100, accountA.snapshot().balance().solde());
        assertAmount(0, accountA.snapshot().reserved());
        assertAmount(100, accountA.available());
        assertEquals(2, tx.entries().size());
        assertTrue(isDoubleEntryBalanced(tx));
    }

    @Test
    void should_refuse_to_capture_a_hold_of_another_transaction() {
        Transaction tx = initiateCashOut();
        FundsHold foreign = ledger.hold(initiateCashOut(), accountA);

        assertThrows(IllegalArgumentException.class,
                () -> ledger.capture(tx, foreign, accountA, floatAccount));
    }

    @Test
    void should_release_a_hold_back_to_the_available_balance() {
        Transaction tx = initiateCashOut();
        FundsHold hold = ledger.hold(tx, accountA);

        ledger.release(hold, accountA);

        assertEquals(HoldStatus.RELEASED, hold.snapshot().status());
        assertAmount(200, accountA.available());
        assertAmount(200, accountA.snapshot().balance().solde());
        assertTrue(tx.entries().isEmpty());
    }

    @Test
    void should_write_the_mirrored_pair_on_reverse() {
        Transaction tx = postedTransfer();
        ledger.reverse(tx, accountA, accountB);

        assertEquals(List.of(idOf(accountA), idOf(accountB)), accountsOf(tx, LedgerEntryType.DEBIT));
        assertEquals(List.of(idOf(accountB), idOf(accountA)), accountsOf(tx, LedgerEntryType.CREDIT));
    }

    @Test
    void should_restore_both_balances_on_reverse() {
        Transaction tx = postedTransfer();
        ledger.reverse(tx, accountA, accountB);

        assertAmount(200, accountA.snapshot().balance().solde());
        assertAmount(0, accountB.snapshot().balance().solde());
    }

    @Test
    void should_append_the_compensating_pair_without_removing_the_originals() {
        Transaction tx = postedTransfer();
        ledger.reverse(tx, accountA, accountB);
        assertEquals(4, tx.entries().size());
    }

    @Test
    void should_keep_double_entry_balanced_after_reverse() {
        Transaction tx = postedTransfer();
        ledger.reverse(tx, accountA, accountB);
        assertTrue(isDoubleEntryBalanced(tx));
    }

    @Test
    void should_reverse_the_transaction_amount() {
        Transaction tx = postedTransfer();
        ledger.reverse(tx, accountA, accountB);

        assertAmount(200, sumByType(tx, LedgerEntryType.DEBIT));
        assertAmount(200, sumByType(tx, LedgerEntryType.CREDIT));
    }

    @Test
    void should_refuse_a_reversal_that_would_spend_reserved_funds() {
        Transaction tx = postedTransfer();
        accountB.reserve(xaf(50));

        assertThrows(InsufficientFundsException.class, () -> ledger.reverse(tx, accountA, accountB));
    }

    @Test
    void should_refuse_to_reverse_between_accounts_that_are_not_the_parties() {
        Transaction tx = postedTransfer();

        assertThrows(IllegalArgumentException.class, () -> ledger.reverse(tx, accountB, accountA));
    }
}
