package com.geekersjoel237.koracore.domain.vo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdTest {

    // ── Construction valide ───────────────────────────────────────────────────

    @Test
    void should_create_id_when_value_is_provided() {
        assertDoesNotThrow(() -> new Id("001"));
    }

    @Test
    void should_create_id_when_value_is_uuid_format() {
        assertDoesNotThrow(() -> new Id("abc-123"));
    }

    @Test
    void should_generate_valid_uuid_when_generate_called() {
        Id generated = Id.generate();
        assertNotNull(generated);
        assertNotNull(generated.value());
        assertFalse(generated.value().isBlank());
    }

    // ── Unicité ───────────────────────────────────────────────────────────────

    @Test
    void should_generate_different_ids_on_each_call() {
        Id first = Id.generate();
        Id second = Id.generate();
        assertNotEquals(first.value(), second.value());
    }

    // ── Validation ───────────────────────────────────────────────────────────

    @Test
    void should_throw_when_value_is_null() {
        assertThrows(IllegalArgumentException.class, () -> new Id(null));
    }

    @Test
    void should_throw_when_value_is_empty() {
        assertThrows(IllegalArgumentException.class, () -> new Id(""));
    }

    @Test
    void should_throw_when_value_is_blank() {
        assertThrows(IllegalArgumentException.class, () -> new Id("   "));
    }

    // ── Égalité (record) ─────────────────────────────────────────────────────

    @Test
    void should_be_equal_when_same_value() {
        assertEquals(new Id("001"), new Id("001"));
    }

    @Test
    void should_not_be_equal_when_different_value() {
        assertNotEquals(new Id("001"), new Id("002"));
    }

    // ── Immuabilité ───────────────────────────────────────────────────────────

    @Test
    void should_always_return_same_value() {
        Id id = new Id("001");
        assertEquals(id.value(), id.value());
    }
}