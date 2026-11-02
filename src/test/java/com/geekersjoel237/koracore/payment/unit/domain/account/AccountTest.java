package com.geekersjoel237.koracore.payment.unit.domain.account;

import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.domain.enums.ResourceType;
import com.geekersjoel237.koracore.payment.domain.exception.InsufficientFundsException;
import com.geekersjoel237.koracore.payment.domain.exception.InvalidAccountException;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    private static final Id CUSTOMER_ID = new Id("cust-001");
    private static final Id PROVIDER_ID = new Id("prov-001");

    @Test
    void should_create_customer_account_with_zero_balance() {
        Account account = Account.createCustomerAccount(
                Id.generate(), CUSTOMER_ID);
        assertEquals(Amount.of(BigDecimal.ZERO, "XAF"), account.snapshot().balance().solde());
    }

    @Test
    void should_create_float_account_with_zero_balance() {
        Account account = Account.createFloatAccount(
                Id.generate(), PROVIDER_ID);
        assertEquals(Amount.of(BigDecimal.ZERO, "XAF"), account.snapshot().balance().solde());
    }

    @Test
    void should_create_customer_account_with_valid_account_type(){
        Account account = Account.createCustomerAccount(
                Id.generate(), CUSTOMER_ID);
        assertEquals(CUSTOMER_ID, account.snapshot().accountType().resourceId());
        assertEquals(ResourceType.CUSTOMER_ACCOUNT, account.snapshot().accountType().resourceType());
    }

    @Test
    void should_create_float_account_with_valid_account_type(){
        Account account = Account.createFloatAccount(
                Id.generate(), PROVIDER_ID);
        assertEquals(PROVIDER_ID, account.snapshot().accountType().resourceId());
        assertEquals(ResourceType.FLOAT_ACCOUNT, account.snapshot().accountType().resourceType());

    }

    @Test
    void should_generate_account_number_with_correct_format() {
        Account account = Account.createCustomerAccount(
                Id.generate(), CUSTOMER_ID);
        assertTrue(account.snapshot().accountNumber().matches("ACC-\\d{8}-[A-Z0-9]{8}"));
    }

    @Test
    void should_generate_account_number_using_last_8_hex_chars_of_id() {
        // UUID without dashes: 550e8400e29b41d4a716446655440000 → last 8 = 55440000
        Id id = new Id("550e8400-e29b-41d4-a716-446655440000");
        Account account = Account.createCustomerAccount(id, CUSTOMER_ID);
        assertTrue(account.snapshot().accountNumber().endsWith("-55440000"));
    }

    @Test
    void should_be_active_by_default() {
        Account account = Account.createCustomerAccount(
                Id.generate(), CUSTOMER_ID);
        assertTrue(account.isActive());
    }

    @Test
    void should_be_blocked_after_block_called() {
        Account account = Account.createCustomerAccount(
                Id.generate(), CUSTOMER_ID);
        account.block();
        assertTrue(account.isBlocked());
    }

    @Test
    void should_not_be_active_when_blocked() {
        Account account = Account.createCustomerAccount(
                Id.generate(), CUSTOMER_ID);
        account.block();
        assertFalse(account.isActive());
    }

    @Test
    void should_increase_balance_when_credited() {
        Account account = Account.createCustomerAccount(
                Id.generate(), CUSTOMER_ID);
        account.credit(Amount.of(BigDecimal.valueOf(100), "XAF"));
        assertEquals(Amount.of(BigDecimal.valueOf(100), "XAF"),
                account.snapshot().balance().solde());
    }

    @Test
    void should_allow_multiple_credits() {
        Account account = Account.createCustomerAccount(
                Id.generate(), CUSTOMER_ID);
        account.credit(Amount.of(BigDecimal.valueOf(100), "XAF"));
        account.credit(Amount.of(BigDecimal.valueOf(50), "XAF"));
        assertEquals(Amount.of(BigDecimal.valueOf(150), "XAF"),
                account.snapshot().balance().solde());
    }

    @Test
    void should_decrease_balance_when_customer_account_debited() {
        Account account = Account.createCustomerAccount(
                Id.generate(), CUSTOMER_ID);
        account.credit(Amount.of(BigDecimal.valueOf(100), "XAF"));
        account.debit(Amount.of(BigDecimal.valueOf(40), "XAF"));
        assertEquals(Amount.of(BigDecimal.valueOf(60), "XAF"),
                account.snapshot().balance().solde());
    }

    @Test
    void should_throw_when_customer_account_balance_insufficient() {
        Account account = Account.createCustomerAccount(
                Id.generate(), CUSTOMER_ID);
        assertThrows(InsufficientFundsException.class,
                () -> account.debit(Amount.of(BigDecimal.valueOf(100), "XAF")));
    }

    @Test
    void should_not_throw_when_float_account_debited_without_sufficient_balance() {
        Account floatAccount = Account.createFloatAccount(
                Id.generate(), PROVIDER_ID);
        assertDoesNotThrow(
                () -> floatAccount.debit(Amount.of(BigDecimal.valueOf(100), "XAF")));
    }

    @Test
    void should_not_track_balance_for_float_account_debit() {
        Account floatAccount = Account.createFloatAccount(Id.generate(), PROVIDER_ID);
        Amount largeDebit = Amount.of(BigDecimal.valueOf(1_000_000), "XAF");

        floatAccount.debit(largeDebit);

        assertEquals(Amount.of(BigDecimal.ZERO, "XAF"),
                floatAccount.snapshot().balance().solde(),
                "Float account balance must remain 0 after debit — it is never decremented");
    }

    @Test
    void should_reconstruct_account_from_snapshot_without_validation() {
        Account original = Account.createCustomerAccount(
                Id.generate(), CUSTOMER_ID);
        Account rebuilt = Account.createFromSnapshot(original.snapshot());
        assertEquals(original.snapshot().accountId(), rebuilt.snapshot().accountId());
    }

    @Test
    void should_reconstruct_blocked_account_from_snapshot() {
        Account account = Account.createCustomerAccount(
                Id.generate(), CUSTOMER_ID);
        account.block();
        Account rebuilt = Account.createFromSnapshot(account.snapshot());
        assertTrue(rebuilt.snapshot().isBlocked());
    }

    private static Amount xaf(long value) {
        return Amount.of(BigDecimal.valueOf(value), "XAF");
    }

    private static Account walletWith(long balance) {
        Account wallet = Account.createCustomerAccount(Id.generate(), CUSTOMER_ID);
        wallet.credit(xaf(balance));
        return wallet;
    }

    private static void assertAmount(long expected, Amount actual) {
        assertEquals(0, actual.value().compareTo(BigDecimal.valueOf(expected)));
    }

    @Test
    void should_make_the_whole_balance_available_when_nothing_is_reserved() {
        assertAmount(1000, walletWith(1000).available());
    }

    @Test
    void should_take_a_reservation_out_of_the_available_balance_only() {
        Account wallet = walletWith(1000);

        wallet.reserve(xaf(300));

        assertAmount(700, wallet.available());
        assertAmount(1000, wallet.snapshot().balance().solde());
    }

    @Test
    void should_refuse_to_reserve_more_than_is_available() {
        Account wallet = walletWith(1000);
        wallet.reserve(xaf(800));

        assertThrows(InsufficientFundsException.class, () -> wallet.reserve(xaf(300)));
    }

    @Test
    void should_refuse_a_debit_that_would_spend_reserved_funds() {
        Account wallet = walletWith(1000);
        wallet.reserve(xaf(800));

        assertThrows(InsufficientFundsException.class, () -> wallet.debit(xaf(300)));
    }

    @Test
    void should_give_a_released_reservation_back_to_the_available_balance() {
        Account wallet = walletWith(1000);
        wallet.reserve(xaf(300));

        wallet.release(xaf(300));

        assertAmount(1000, wallet.available());
    }

    @Test
    void should_turn_a_reservation_into_a_debit_without_moving_the_available_balance() {
        Account wallet = walletWith(1000);
        wallet.reserve(xaf(300));

        wallet.debitReserved(xaf(300));

        assertAmount(700, wallet.snapshot().balance().solde());
        assertAmount(0, wallet.snapshot().reserved());
        assertAmount(700, wallet.available());
    }

    @Test
    void should_refuse_to_settle_more_than_is_reserved() {
        Account wallet = walletWith(1000);
        wallet.reserve(xaf(300));

        assertThrows(IllegalStateException.class, () -> wallet.release(xaf(400)));
        assertThrows(IllegalStateException.class, () -> wallet.debitReserved(xaf(400)));
    }

    @Test
    void should_refuse_a_reservation_on_the_float_account() {
        Account floatAccount = Account.createFloatAccount(Id.generate(), PROVIDER_ID);

        assertThrows(InvalidAccountException.class, () -> floatAccount.reserve(xaf(100)));
    }

    @Test
    void should_not_track_balance_for_float_account_credit() {
        Account floatAccount = Account.createFloatAccount(Id.generate(), PROVIDER_ID);

        floatAccount.credit(xaf(500));

        assertAmount(0, floatAccount.snapshot().balance().solde());
    }

    @Test
    void should_keep_the_reserved_amount_through_a_snapshot() {
        Account wallet = walletWith(1000);
        wallet.reserve(xaf(300));

        assertAmount(700, Account.createFromSnapshot(wallet.snapshot()).available());
    }
}