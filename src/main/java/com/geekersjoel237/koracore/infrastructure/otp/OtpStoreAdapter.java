package com.geekersjoel237.koracore.infrastructure.otp;

import com.geekersjoel237.koracore.domain.port.OtpStore;
import com.geekersjoel237.koracore.domain.vo.Otp;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory OTP store. Suitable for single-instance deployments and testing.
 * Replace with a Redis-backed implementation for multi-instance production use.
 */
@Component
public class OtpStoreAdapter implements OtpStore {

    private final ConcurrentHashMap<String, Otp> store = new ConcurrentHashMap<>();
    private final Clock clock;

    public OtpStoreAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void save(String key, Otp otp) {
        store.put(key, otp);
    }

    @Override
    public Optional<Otp> get(String key) {
        Otp otp = store.get(key);
        if (otp == null || otp.isExpired(clock)) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(otp);
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }
}