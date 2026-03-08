package com.geekersjoel237.koracore.domain.vo;

import com.geekersjoel237.koracore.domain.exception.CurrencyMismatchException;
import com.geekersjoel237.koracore.domain.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BalanceTest {

    // ── Construction valide ───────────────────────────────────────────────────

    @Test
    void should_create_balance_when_amount_is_valid() {
        assertDoesNotThrow(() -> Balance.of(Amount.of(BigDecimal.valueOf(100), "XOF")));
    }

    @Test
    void should_create_zero_balance_when_using_zero_factory() {
        assertEquals(
                Amount.of(BigDecimal.ZERO, "XOF"),
                Balance.zero("XOF").solde()
        );
    }

    // ── Validation construction ───────────────────────────────────────────────

    @Test
    void should_throw_when_amount_is_null() {
        assertThrows(IllegalArgumentException.class, () -> Balance.of(null));
    }

    // ── solde ─────────────────────────────────────────────────────────────────

    @Test
    void should_return_correct_amount_when_solde_called() {
        Amount amount = Amount.of(BigDecimal.valueOf(100), "XOF");
        assertEquals(amount, Balance.of(amount).solde());
    }

    // ── credit ────────────────────────────────────────────────────────────────

    @Test
    void should_increase_balance_when_credited() {
        Balance result = Balance.of(Amount.of(BigDecimal.valueOf(100), "XOF"))
                .credit(Amount.of(BigDecimal.valueOf(50), "XOF"));
        assertEquals(Amount.of(BigDecimal.valueOf(150), "XOF"), result.solde());
    }

    @Test
    void should_not_mutate_original_when_credited() {
        Balance b = Balance.of(Amount.of(BigDecimal.valueOf(100), "XOF"));
        b.credit(Amount.of(BigDecimal.valueOf(50), "XOF"));
        assertEquals(Amount.of(BigDecimal.valueOf(100), "XOF"), b.solde());
    }

    @Test
    void should_allow_credit_on_zero_balance() {
        Balance result = Balance.zero("XOF")
                .credit(Amount.of(BigDecimal.valueOf(100), "XOF"));
        assertEquals(Amount.of(BigDecimal.valueOf(100), "XOF"), result.solde());
    }

    @Test
    void should_throw_when_credit_currency_mismatch() {
        assertThrows(CurrencyMismatchException.class,
                () -> Balance.of(Amount.of(BigDecimal.valueOf(100), "XOF"))
                        .credit(Amount.of(BigDecimal.valueOf(50), "EUR")));
    }

    // ── debit ─────────────────────────────────────────────────────────────────

    @Test
    void should_decrease_balance_when_debited() {
        Balance result = Balance.of(Amount.of(BigDecimal.valueOf(100), "XOF"))
                .debit(Amount.of(BigDecimal.valueOf(50), "XOF"));
        assertEquals(Amount.of(BigDecimal.valueOf(50), "XOF"), result.solde());
    }

    @Test
    void should_allow_debit_exact_balance() {
        Balance result = Balance.of(Amount.of(BigDecimal.valueOf(100), "XOF"))
                .debit(Amount.of(BigDecimal.valueOf(100), "XOF"));
        assertEquals(Amount.of(BigDecimal.ZERO, "XOF"), result.solde());
    }

    @Test
    void should_not_mutate_original_when_debited() {
        Balance b = Balance.of(Amount.of(BigDecimal.valueOf(100), "XOF"));
        b.debit(Amount.of(BigDecimal.valueOf(50), "XOF"));
        assertEquals(Amount.of(BigDecimal.valueOf(100), "XOF"), b.solde());
    }

    @Test
    void should_throw_when_debit_exceeds_balance() {
        assertThrows(InsufficientFundsException.class,
                () -> Balance.of(Amount.of(BigDecimal.valueOf(100), "XOF"))
                        .debit(Amount.of(BigDecimal.valueOf(150), "XOF")));
    }

    @Test
    void should_throw_when_debit_on_zero_balance() {
        assertThrows(InsufficientFundsException.class,
                () -> Balance.zero("XOF")
                        .debit(Amount.of(BigDecimal.valueOf(1), "XOF")));
    }
}