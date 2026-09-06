package com.geekersjoel237.koracore.shared.unit.domain;

import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.exception.CurrencyMismatchException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AmountTest {

    // ── Construction valide ───────────────────────────────────────────────────

    @Test
    void should_create_amount_when_value_and_currency_are_valid() {
        assertDoesNotThrow(() -> Amount.of(BigDecimal.valueOf(100), "XAF"));
    }

    @Test
    void should_create_amount_when_value_is_zero() {
        assertDoesNotThrow(() -> Amount.of(BigDecimal.ZERO, "XAF"));
    }

    // ── Précision BigDecimal — cas critique fintech ───────────────────────────

    @Test
    void should_handle_decimal_precision_without_floating_point_error() {
        Amount result = Amount.of(new BigDecimal("0.1"), "XAF")
                .add(Amount.of(new BigDecimal("0.2"), "XAF"));
        assertEquals(new BigDecimal("0.3"), result.value());
    }

    // ── Validation construction ───────────────────────────────────────────────

    @Test
    void should_throw_when_value_is_null() {
        assertThrows(IllegalArgumentException.class,
                () -> Amount.of(null, "XAF"));
    }

    @Test
    void should_throw_when_value_is_negative() {
        assertThrows(IllegalArgumentException.class,
                () -> Amount.of(BigDecimal.valueOf(-1), "XAF"));
    }

    @Test
    void should_throw_when_currency_is_null() {
        assertThrows(IllegalArgumentException.class,
                () -> Amount.of(BigDecimal.valueOf(100), null));
    }

    @Test
    void should_throw_when_currency_is_blank() {
        assertThrows(IllegalArgumentException.class,
                () -> Amount.of(BigDecimal.valueOf(100), ""));
    }

    // ── add ───────────────────────────────────────────────────────────────────

    @Test
    void should_return_correct_sum_when_adding_two_amounts() {
        Amount result = Amount.of(BigDecimal.valueOf(100), "XAF")
                .add(Amount.of(BigDecimal.valueOf(50), "XAF"));
        assertEquals(Amount.of(BigDecimal.valueOf(150), "XAF"), result);
    }

    @Test
    void should_not_mutate_original_when_adding() {
        Amount a = Amount.of(BigDecimal.valueOf(100), "XAF");
        a.add(Amount.of(BigDecimal.valueOf(50), "XAF"));
        assertEquals(BigDecimal.valueOf(100), a.value());
    }

    @Test
    void should_throw_when_adding_different_currencies() {
        assertThrows(CurrencyMismatchException.class,
                () -> Amount.of(BigDecimal.valueOf(100), "XAF")
                        .add(Amount.of(BigDecimal.valueOf(50), "EUR")));
    }

    // ── subtract ──────────────────────────────────────────────────────────────

    @Test
    void should_return_correct_difference_when_subtracting() {
        Amount result = Amount.of(BigDecimal.valueOf(100), "XAF")
                .subtract(Amount.of(BigDecimal.valueOf(50), "XAF"));
        assertEquals(Amount.of(BigDecimal.valueOf(50), "XAF"), result);
    }

    @Test
    void should_return_zero_when_subtracting_equal_amounts() {
        Amount result = Amount.of(BigDecimal.valueOf(100), "XAF")
                .subtract(Amount.of(BigDecimal.valueOf(100), "XAF"));
        assertEquals(Amount.of(BigDecimal.ZERO, "XAF"), result);
    }

    @Test
    void should_not_mutate_original_when_subtracting() {
        Amount a = Amount.of(BigDecimal.valueOf(100), "XAF");
        a.subtract(Amount.of(BigDecimal.valueOf(50), "XAF"));
        assertEquals(BigDecimal.valueOf(100), a.value());
    }

    @Test
    void should_throw_when_subtracting_more_than_available() {
        assertThrows(IllegalArgumentException.class,
                () -> Amount.of(BigDecimal.valueOf(100), "XAF")
                        .subtract(Amount.of(BigDecimal.valueOf(150), "XAF")));
    }

    @Test
    void should_throw_when_subtracting_different_currencies() {
        assertThrows(CurrencyMismatchException.class,
                () -> Amount.of(BigDecimal.valueOf(100), "XAF")
                        .subtract(Amount.of(BigDecimal.valueOf(50), "EUR")));
    }

    // ── isGreaterThan ─────────────────────────────────────────────────────────

    @Test
    void should_return_true_when_first_amount_is_greater() {
        assertTrue(Amount.of(BigDecimal.valueOf(100), "XAF")
                .isGreaterThan(Amount.of(BigDecimal.valueOf(50), "XAF")));
    }

    @Test
    void should_return_false_when_first_amount_is_less() {
        assertFalse(Amount.of(BigDecimal.valueOf(50), "XAF")
                .isGreaterThan(Amount.of(BigDecimal.valueOf(100), "XAF")));
    }

    @Test
    void should_return_false_when_amounts_are_equal_for_isGreaterThan() {
        assertFalse(Amount.of(BigDecimal.valueOf(100), "XAF")
                .isGreaterThan(Amount.of(BigDecimal.valueOf(100), "XAF")));
    }

    @Test
    void should_throw_when_comparing_different_currencies_with_isGreaterThan() {
        assertThrows(CurrencyMismatchException.class,
                () -> Amount.of(BigDecimal.valueOf(100), "XAF")
                        .isGreaterThan(Amount.of(BigDecimal.valueOf(50), "EUR")));
    }

    // ── isGreaterThanOrEqual ──────────────────────────────────────────────────

    @Test
    void should_return_true_when_amounts_are_equal() {
        assertTrue(Amount.of(BigDecimal.valueOf(100), "XAF")
                .isGreaterThanOrEqual(Amount.of(BigDecimal.valueOf(100), "XAF")));
    }

    @Test
    void should_return_true_when_first_is_greater() {
        assertTrue(Amount.of(BigDecimal.valueOf(150), "XAF")
                .isGreaterThanOrEqual(Amount.of(BigDecimal.valueOf(100), "XAF")));
    }

    @Test
    void should_return_false_when_first_is_less() {
        assertFalse(Amount.of(BigDecimal.valueOf(50), "XAF")
                .isGreaterThanOrEqual(Amount.of(BigDecimal.valueOf(100), "XAF")));
    }

    // ── Égalité (record) ──────────────────────────────────────────────────────

    @Test
    void should_be_equal_when_same_value_and_currency() {
        assertEquals(
                Amount.of(BigDecimal.valueOf(100), "XAF"),
                Amount.of(BigDecimal.valueOf(100), "XAF")
        );
    }

    @Test
    void should_not_be_equal_when_different_currency() {
        assertNotEquals(
                Amount.of(BigDecimal.valueOf(100), "XAF"),
                Amount.of(BigDecimal.valueOf(100), "EUR")
        );
    }

    @Test
    void should_not_be_equal_when_different_value() {
        assertNotEquals(
                Amount.of(BigDecimal.valueOf(100), "XAF"),
                Amount.of(BigDecimal.valueOf(200), "XAF")
        );
    }

    // ── isStrictPositive ──────────────────────────────────────────────────────

    @Test
    void should_return_true_when_value_is_positive() {
        assertTrue(Amount.of(BigDecimal.valueOf(100), "XAF").isStrictPositive());
    }

    @Test
    void should_return_false_when_value_is_zero() {
        assertFalse(Amount.of(BigDecimal.ZERO, "XAF").isStrictPositive());
    }

    @Test
    void should_return_false_when_value_is_zero_with_decimals() {
        assertFalse(Amount.of(new BigDecimal("0.00"), "XAF").isStrictPositive());
    }

    // ── equals(Amount) — scale-insensitive ───────────────────────────────────

    @Test
    void should_return_true_when_same_value_and_currency_via_amount_equals() {
        assertTrue(Amount.of(new BigDecimal("100"), "XAF")
                .equals(Amount.of(new BigDecimal("100"), "XAF")));
    }

    @Test
    void should_return_true_when_same_value_different_scale() {
        // compareTo("100.00", "100.0") == 0 → equal
        assertTrue(Amount.of(new BigDecimal("100.00"), "XAF")
                .equals(Amount.of(new BigDecimal("100.0"), "XAF")));
    }

    @Test
    void should_return_false_when_different_value_via_amount_equals() {
        assertFalse(Amount.of(new BigDecimal("100"), "XAF")
                .equals(Amount.of(new BigDecimal("200"), "XAF")));
    }

    @Test
    void should_return_false_when_different_currency_via_amount_equals() {
        assertFalse(Amount.of(new BigDecimal("100"), "XAF")
                .equals(Amount.of(new BigDecimal("100"), "EUR")));
    }

    @Test
    void should_return_false_when_other_is_null() {
        assertFalse(Amount.of(new BigDecimal("100"), "XAF").equals((Amount) null));
    }
}