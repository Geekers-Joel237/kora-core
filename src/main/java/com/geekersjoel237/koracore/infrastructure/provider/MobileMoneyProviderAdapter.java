package com.geekersjoel237.koracore.infrastructure.provider;

import com.geekersjoel237.koracore.domain.port.ProviderPort;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.AuthorizationResult;
import com.geekersjoel237.koracore.domain.vo.CaptureResult;
import com.geekersjoel237.koracore.domain.vo.ReverseResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Placeholder adapter for the Mobile Money provider.
 * Replace with a real HTTP client implementation in later roadmap stages.
 */
@Component
public class MobileMoneyProviderAdapter implements ProviderPort {

    @Override
    public void credit(Amount amount, String paymentMethod) {
        // Step 0: stub — always succeeds
    }

    @Override
    public void debit(Amount amount, String paymentMethod) {
        // Step 0: stub — always succeeds
    }

    @Override
    public void send(Amount amount, String paymentMethod) {
        // Step 0: stub — always succeeds
    }

    @Override
    public AuthorizationResult authorize(Amount amount, String paymentMethod, String correlationId) {
        // Step 0: stub — always succeeds
        return new AuthorizationResult(
                UUID.randomUUID().toString(),
                Instant.now().plus(15, ChronoUnit.MINUTES),
                true);
    }

    @Override
    public CaptureResult capture(String authorizationReference, String correlationId) {
        // Step 0: stub — always succeeds
        return new CaptureResult(UUID.randomUUID().toString(), true);
    }

    @Override
    public ReverseResult reverse(String reference, Amount amount, String correlationId) {
        // Step 0: stub — always succeeds
        return new ReverseResult(UUID.randomUUID().toString(), true);
    }
}