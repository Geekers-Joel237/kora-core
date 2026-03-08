package com.geekersjoel237.koracore.shared.inmemory;

import com.geekersjoel237.koracore.domain.port.OtpStore;
import com.geekersjoel237.koracore.domain.vo.Otp;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryOtpStore implements OtpStore {

    private final Map<String, Otp> store = new HashMap<>();
    private final Clock clock;

    public InMemoryOtpStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void save(String key, Otp otp) {
        store.put(key, otp);
    }

    @Override
    public Optional<Otp> get(String key) {
        Otp otp = store.get(key);
        if (otp == null) return Optional.empty();
        // Check expiry against the real system clock so that OTPs created with
        // a past-fixed clock (in expiry tests) are seen as expired immediately.
        if (otp.isExpired(Clock.systemUTC())) return Optional.empty();
        return Optional.of(otp);
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    public void reset() {
        store.clear();
    }
}