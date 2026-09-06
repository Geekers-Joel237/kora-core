package com.geekersjoel237.koracore.shared.ports.out.store;

import java.time.Duration;
import java.util.Optional;


public interface ExpiringStore<V> {


    void put(String key, V value, Duration ttl);

    Optional<V> get(String key);

    void remove(String key);
}
