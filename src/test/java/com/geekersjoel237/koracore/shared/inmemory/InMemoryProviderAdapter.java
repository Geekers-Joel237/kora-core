package com.geekersjoel237.koracore.shared.inmemory;

import com.geekersjoel237.koracore.domain.exception.ProviderException;
import com.geekersjoel237.koracore.domain.port.ProviderPort;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.AuthorizationResult;
import com.geekersjoel237.koracore.domain.vo.CaptureResult;
import com.geekersjoel237.koracore.domain.vo.ReverseResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class InMemoryProviderAdapter implements ProviderPort {

    private Behavior behavior;

    public InMemoryProviderAdapter(Behavior behavior) {
        this.behavior = behavior;
    }

    public void setBehavior(Behavior behavior) {
        this.behavior = behavior;
    }

    @Override
    public AuthorizationResult authorize(Amount amount, String paymentMethod, String correlationId) {
        if (behavior == Behavior.FAIL || behavior == Behavior.FAIL_ON_AUTHORIZE)
            throw new ProviderException("Provider simulated authorization failure");
        return new AuthorizationResult(
                UUID.randomUUID().toString(),
                Instant.now().plus(15, ChronoUnit.MINUTES),
                true);
    }

    // ── Lifecycle methods ─────────────────────────────────────────────────────

    @Override
    public CaptureResult capture(String authorizationReference, String correlationId) {
        if (behavior == Behavior.FAIL_ON_CAPTURE)
            throw new ProviderException("Provider simulated capture failure");
        return new CaptureResult(UUID.randomUUID().toString(), true);
    }

    @Override
    public ReverseResult reverse(String reference, Amount amount, String correlationId) {
        return new ReverseResult(UUID.randomUUID().toString(), true);
    }

    public enum Behavior {
        SUCCESS,
        /**
         * Fails on authorize() — the default failure mode. Alias: {@link #FAIL}.
         */
        FAIL_ON_AUTHORIZE,
        /**
         * Fails on capture() — authorize() still succeeds.
         */
        FAIL_ON_CAPTURE,
        /**
         * Backward-compatible alias for {@link #FAIL_ON_AUTHORIZE}.
         */
        FAIL
    }
}