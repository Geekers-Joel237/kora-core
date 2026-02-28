package com.geekersjoel237.koracore.domain.vo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumberTest {

    // ── Construction valide ───────────────────────────────────────────────────

    @Test
    void should_create_phone_number_when_valid_prefix_and_number() {
        assertDoesNotThrow(() -> PhoneNumber.of("+225", "0700000000"));
    }

    // ── fullNumber ────────────────────────────────────────────────────────────

    @Test
    void should_return_full_number_when_concatenating_prefix_and_number() {
        PhoneNumber pn = PhoneNumber.of("+225", "0700000000");
        assertEquals("+2250700000000", pn.fullNumber());
    }

    // ── Accesseurs ────────────────────────────────────────────────────────────

    @Test
    void should_return_correct_prefix() {
        assertEquals("+225", PhoneNumber.of("+225", "0700000000").prefix());
    }

    @Test
    void should_return_correct_number() {
        assertEquals("0700000000", PhoneNumber.of("+225", "0700000000").number());
    }

    // ── Validation prefix ─────────────────────────────────────────────────────

    @Test
    void should_throw_when_prefix_is_null() {
        assertThrows(IllegalArgumentException.class,
                () -> PhoneNumber.of(null, "0700000000"));
    }

    @Test
    void should_throw_when_prefix_is_blank() {
        assertThrows(IllegalArgumentException.class,
                () -> PhoneNumber.of("", "0700000000"));
    }

    @Test
    void should_throw_when_prefix_does_not_start_with_plus() {
        assertThrows(IllegalArgumentException.class,
                () -> PhoneNumber.of("225", "0700000000"));
    }

    @Test
    void should_throw_when_prefix_contains_letters() {
        assertThrows(IllegalArgumentException.class,
                () -> PhoneNumber.of("+abc", "0700000000"));
    }

    @Test
    void should_throw_when_prefix_too_long() {
        assertThrows(IllegalArgumentException.class,
                () -> PhoneNumber.of("+12345", "0700000000"));
    }

    @Test
    void should_throw_when_prefix_is_only_plus() {
        assertThrows(IllegalArgumentException.class,
                () -> PhoneNumber.of("+", "0700000000"));
    }

    // ── Validation number ─────────────────────────────────────────────────────

    @Test
    void should_throw_when_number_is_null() {
        assertThrows(IllegalArgumentException.class,
                () -> PhoneNumber.of("+225", null));
    }

    @Test
    void should_throw_when_number_is_blank() {
        assertThrows(IllegalArgumentException.class,
                () -> PhoneNumber.of("+225", ""));
    }

    @Test
    void should_throw_when_number_contains_letters() {
        assertThrows(IllegalArgumentException.class,
                () -> PhoneNumber.of("+225", "ABCDEFGHIJ"));
    }

    @Test
    void should_throw_when_number_too_short() {
        assertThrows(IllegalArgumentException.class,
                () -> PhoneNumber.of("+225", "070000"));
    }

    @Test
    void should_throw_when_number_too_long() {
        assertThrows(IllegalArgumentException.class,
                () -> PhoneNumber.of("+225", "0700000000000000"));
    }

    // ── Égalité (record) ──────────────────────────────────────────────────────

    @Test
    void should_be_equal_when_same_prefix_and_number() {
        assertEquals(
                PhoneNumber.of("+225", "0700000000"),
                PhoneNumber.of("+225", "0700000000")
        );
    }

    @Test
    void should_not_be_equal_when_different_number() {
        assertNotEquals(
                PhoneNumber.of("+225", "0700000000"),
                PhoneNumber.of("+225", "0701010101")
        );
    }

    // ── Immuabilité ───────────────────────────────────────────────────────────

    @Test
    void should_always_return_same_full_number() {
        PhoneNumber pn = PhoneNumber.of("+225", "0700000000");
        assertEquals(pn.fullNumber(), pn.fullNumber());
    }
}