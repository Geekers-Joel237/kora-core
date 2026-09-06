package com.geekersjoel237.koracore.shared.adapters.out.store;

import com.geekersjoel237.koracore.shared.ports.out.store.ExpiringStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


public class InMemoryExpiringStore<V> implements ExpiringStore<V> {

    private final ConcurrentHashMap<String, Entry<V>> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryExpiringStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void put(String key, V value, Duration ttl) {
        if (value == null)
            throw new IllegalArgumentException("Cannot store null under key: " + key);
        if (ttl == null || ttl.isNegative() || ttl.isZero())
            throw new IllegalArgumentException("TTL must be strictly positive, got: " + ttl);

        entries.put(key, new Entry<>(value, Instant.now(clock).plus(ttl)));
    }

    @Override
    public Optional<V> get(String key) {
        Entry<V> entry = entries.get(key);
        if (entry == null) return Optional.empty();

        if (entry.hasExpiredBy(Instant.now(clock))) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    @Override
    public void remove(String key) {
        entries.remove(key);
    }

    private record Entry<V>(V value, Instant expiresAt) {

        boolean hasExpiredBy(Instant now) {
            return now.isAfter(expiresAt);
        }
    }
}
