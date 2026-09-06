package com.geekersjoel237.koracore.payment.unit.domain.account;

import com.geekersjoel237.koracore.payment.domain.vo.Balance;
import com.geekersjoel237.koracore.shared.domain.exception.CurrencyMismatchException;
import com.geekersjoel237.koracore.payment.domain.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;

import static org.junit.jupiter.api.Assertions.*;

class BalanceTest {

    // ── Construction valide ───────────────────────────────────────────────────

    @Test
    void should_create_balance_when_amount_is_valid() {
        assertDoesNotThrow(() -> Balance.of(Amount.of(BigDecimal.valueOf(100), "XAF")));
    }

    @Test
    void should_create_zero_balance_when_using_zero_factory() {
        assertEquals(
                Amount.of(BigDecimal.ZERO, "XAF"),
                Balance.zero("XAF").solde()
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
        Amount amount = Amount.of(BigDecimal.valueOf(100), "XAF");
        assertEquals(amount, Balance.of(amount).solde());
    }

    // ── credit ────────────────────────────────────────────────────────────────

    @Test
    void should_increase_balance_when_credited() {
        Balance result = Balance.of(Amount.of(BigDecimal.valueOf(100), "XAF"))
                .credit(Amount.of(BigDecimal.valueOf(50), "XAF"));
        assertEquals(Amount.of(BigDecimal.valueOf(150), "XAF"), result.solde());
    }

    @Test
    void should_not_mutate_original_when_credited() {
        Balance b = Balance.of(Amount.of(BigDecimal.valueOf(100), "XAF"));
        b.credit(Amount.of(BigDecimal.valueOf(50), "XAF"));
        assertEquals(Amount.of(BigDecimal.valueOf(100), "XAF"), b.solde());
    }

    @Test
    void should_allow_credit_on_zero_balance() {
        Balance result = Balance.zero("XAF")
                .credit(Amount.of(BigDecimal.valueOf(100), "XAF"));
        assertEquals(Amount.of(BigDecimal.valueOf(100), "XAF"), result.solde());
    }

    @Test
    void should_throw_when_credit_currency_mismatch() {
        assertThrows(CurrencyMismatchException.class,
                () -> Balance.of(Amount.of(BigDecimal.valueOf(100), "XAF"))
                        .credit(Amount.of(BigDecimal.valueOf(50), "EUR")));
    }

    // ── debit ─────────────────────────────────────────────────────────────────

    @Test
    void should_decrease_balance_when_debited() {
        Balance result = Balance.of(Amount.of(BigDecimal.valueOf(100), "XAF"))
                .debit(Amount.of(BigDecimal.valueOf(50), "XAF"));
        assertEquals(Amount.of(BigDecimal.valueOf(50), "XAF"), result.solde());
    }

    @Test
    void should_allow_debit_exact_balance() {
        Balance result = Balance.of(Amount.of(BigDecimal.valueOf(100), "XAF"))
                .debit(Amount.of(BigDecimal.valueOf(100), "XAF"));
        assertEquals(Amount.of(BigDecimal.ZERO, "XAF"), result.solde());
    }

    @Test
    void should_not_mutate_original_when_debited() {
        Balance b = Balance.of(Amount.of(BigDecimal.valueOf(100), "XAF"));
        b.debit(Amount.of(BigDecimal.valueOf(50), "XAF"));
        assertEquals(Amount.of(BigDecimal.valueOf(100), "XAF"), b.solde());
    }

    @Test
    void should_throw_when_debit_exceeds_balance() {
        assertThrows(InsufficientFundsException.class,
                () -> Balance.of(Amount.of(BigDecimal.valueOf(100), "XAF"))
                        .debit(Amount.of(BigDecimal.valueOf(150), "XAF")));
    }

    @Test
    void should_throw_when_debit_on_zero_balance() {
        assertThrows(InsufficientFundsException.class,
                () -> Balance.zero("XAF")
                        .debit(Amount.of(BigDecimal.valueOf(1), "XAF")));
    }
}