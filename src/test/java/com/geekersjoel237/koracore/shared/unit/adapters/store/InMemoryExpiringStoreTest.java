package com.geekersjoel237.koracore.shared.unit.adapters.store;

import com.geekersjoel237.koracore.shared.adapters.out.store.InMemoryExpiringStore;
import com.geekersjoel237.koracore.shared.unit.doubles.MutableClock;
import com.geekersjoel237.koracore.shared.ports.out.store.ExpiringStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The store, tested on a value it knows nothing about.
 *
 * <p>{@code String} here is the point: entries expire without the store ever asking
 * what they are. That is what the old {@code OtpStore} could not do — it called
 * {@code isExpired} on the value, so only self-expiring auth types could live in it.
 */
class InMemoryExpiringStoreTest {

    private static final Instant NOON = Instant.parse("2026-01-01T12:00:00Z");
    private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);

    private MutableClock clock;
    private ExpiringStore<String> store;

    @BeforeEach
    void setUp() {
        clock = MutableClock.at(NOON);
        store = new InMemoryExpiringStore<>(clock);
    }

    @Test
    void should_give_back_what_it_was_given() {
        store.put("k", "value", FIVE_MINUTES);

        assertThat(store.get("k")).contains("value");
    }

    @Test
    void should_find_nothing_under_an_unknown_key() {
        assertThat(store.get("nobody")).isEmpty();
    }

    @Test
    void should_keep_an_entry_for_the_whole_window() {
        store.put("k", "value", FIVE_MINUTES);

        clock.advance(Duration.ofMinutes(4).plusSeconds(59));

        assertThat(store.get("k")).contains("value");
    }

    /** The boundary itself is still alive: expiry is strictly after, not at. */
    @Test
    void should_keep_an_entry_at_the_exact_expiry_instant() {
        store.put("k", "value", FIVE_MINUTES);

        clock.advance(FIVE_MINUTES);

        assertThat(store.get("k")).contains("value");
    }

    @Test
    void should_drop_an_entry_once_the_window_has_passed() {
        store.put("k", "value", FIVE_MINUTES);

        clock.advance(FIVE_MINUTES.plusSeconds(1));

        assertThat(store.get("k")).isEmpty();
    }

    /** Expiry is a deletion, not a filter: the entry is gone, not merely hidden. */
    @Test
    void should_forget_an_expired_entry_rather_than_hide_it() {
        store.put("k", "value", FIVE_MINUTES);
        clock.advance(Duration.ofHours(1));

        store.get("k");
        clock.advance(Duration.ofHours(-2));

        assertThat(store.get("k")).isEmpty();
    }

    @Test
    void should_restart_the_window_on_a_second_write() {
        store.put("k", "first", FIVE_MINUTES);
        clock.advance(Duration.ofMinutes(4));
        store.put("k", "second", FIVE_MINUTES);

        clock.advance(Duration.ofMinutes(4));

        assertThat(store.get("k")).contains("second");
    }

    @Test
    void should_remove_an_entry_on_demand() {
        store.put("k", "value", FIVE_MINUTES);

        store.remove("k");

        assertThat(store.get("k")).isEmpty();
    }

    @Test
    void should_not_complain_about_removing_what_is_not_there() {
        store.remove("never-stored");

        assertThat(store.get("never-stored")).isEmpty();
    }

    @Test
    void should_keep_keys_apart() {
        store.put("a", "first", FIVE_MINUTES);
        store.put("b", "second", Duration.ofMinutes(1));

        clock.advance(Duration.ofMinutes(2));

        assertThat(store.get("a")).contains("first");
        assertThat(store.get("b")).isEmpty();
    }

    @Test
    void should_refuse_a_ttl_that_expires_on_arrival() {
        assertThatThrownBy(() -> store.put("k", "value", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly positive");
        assertThatThrownBy(() -> store.put("k", "value", Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_refuse_to_store_nothing() {
        assertThatThrownBy(() -> store.put("k", null, FIVE_MINUTES))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
