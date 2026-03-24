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

public class InMemoryProviderSimulator implements ProviderPort {

    public enum Behavior { SUCCESS, FAIL }

    private Behavior behavior;

    public InMemoryProviderSimulator(Behavior behavior) {
        this.behavior = behavior;
    }

    public void setBehavior(Behavior behavior) {
        this.behavior = behavior;
    }

    private void execute() {
        if (behavior == Behavior.FAIL)
            throw new ProviderException("Provider simulated failure");
    }

    @Override
    public void credit(Amount amount, String paymentMethod) {
        execute();
    }

    @Override
    public void debit(Amount amount, String paymentMethod) {
        execute();
    }

    @Override
    public void send(Amount amount, String paymentMethod) {
        execute();
    }

    @Override
    public AuthorizationResult authorize(Amount amount, String paymentMethod, String correlationId) {
        execute();
        return new AuthorizationResult(
                UUID.randomUUID().toString(),
                Instant.now().plus(15, ChronoUnit.MINUTES),
                true);
    }

    @Override
    public CaptureResult capture(String authorizationReference, String correlationId) {
        execute();
        return new CaptureResult(UUID.randomUUID().toString(), true);
    }

    @Override
    public ReverseResult reverse(String reference, Amount amount, String correlationId) {
        execute();
        return new ReverseResult(UUID.randomUUID().toString(), true);
    }
}