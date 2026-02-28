package com.geekersjoel237.koracore.domain.model;

import com.geekersjoel237.koracore.domain.enums.ResourceType;
import com.geekersjoel237.koracore.domain.exception.InsufficientFundsException;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    private static final Id CUSTOMER_ID = new Id("cust-001");
    private static final Id PROVIDER_ID = new Id("prov-001");

    // ── Construction ──────────────────────────────────────────────────────────

    @Test
    void should_create_customer_account_with_zero_balance() {
        Account account = Account.createCustomerAccount(
                Id.generate(), CUSTOMER_ID);
        assertEquals(Amount.of(BigDecimal.ZERO, "XOF"), account.snapshot().balance().solde());
    }

    @Test
    void should_create_float_account_with_zero_balance() {
        Account account = Account.createFloatAccount(
                Id.generate(), PROVIDER_ID);
        assertEquals(Amount.of(BigDecimal.ZERO, "XOF"), account.snapshot().balance().solde());
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
        assertTrue(account.snapshot().accountNumber().matches("ACC-\\d{8}-[A-Z0-9]{4}"));
    }

    @Test
    void should_generate_account_number_using_last_4_chars_of_id() {
        Id id = new Id("550e8400-e29b-41d4-a716-446655440000");
        Account account = Account.createCustomerAccount(id, CUSTOMER_ID);
        assertTrue(account.snapshot().accountNumber().endsWith("-0000"));
    }

    // ── État ──────────────────────────────────────────────────────────────────

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

    // ── credit ────────────────────────────────────────────────────────────────

    @Test
    void should_increase_balance_when_credited() {
        Account account = Account.createCustomerAccount(
                Id.generate(), CUSTOMER_ID);
        account.credit(Amount.of(BigDecimal.valueOf(100), "XOF"));
        assertEquals(Amount.of(BigDecimal.valueOf(100), "XOF"),
                account.snapshot().balance().solde());
    }

    @Test
    void should_allow_multiple_credits() {
        Account account = Account.createCustomerAccount(
                Id.generate(), CUSTOMER_ID);
        account.credit(Amount.of(BigDecimal.valueOf(100), "XOF"));
        account.credit(Amount.of(BigDecimal.valueOf(50), "XOF"));
        assertEquals(Amount.of(BigDecimal.valueOf(150), "XOF"),
                account.snapshot().balance().solde());
    }

    // ── debit — CUSTOMER_ACCOUNT ──────────────────────────────────────────────

    @Test
    void should_decrease_balance_when_customer_account_debited() {
        Account account = Account.createCustomerAccount(
                Id.generate(), CUSTOMER_ID);
        account.credit(Amount.of(BigDecimal.valueOf(100), "XOF"));
        account.debit(Amount.of(BigDecimal.valueOf(40), "XOF"));
        assertEquals(Amount.of(BigDecimal.valueOf(60), "XOF"),
                account.snapshot().balance().solde());
    }

    @Test
    void should_throw_when_customer_account_balance_insufficient() {
        Account account = Account.createCustomerAccount(
                Id.generate(), CUSTOMER_ID);
        assertThrows(InsufficientFundsException.class,
                () -> account.debit(Amount.of(BigDecimal.valueOf(100), "XOF")));
    }

    // ── debit — FLOAT_ACCOUNT ─────────────────────────────────────────────────

    @Test
    void should_not_throw_when_float_account_debited_without_sufficient_balance() {
        Account floatAccount = Account.createFloatAccount(
                Id.generate(), PROVIDER_ID);
        assertDoesNotThrow(
                () -> floatAccount.debit(Amount.of(BigDecimal.valueOf(100), "XOF")));
    }

    // ── Snapshot — reconstruction ─────────────────────────────────────────────

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
}