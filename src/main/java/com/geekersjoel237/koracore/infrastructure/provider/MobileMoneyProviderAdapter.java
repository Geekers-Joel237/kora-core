package com.geekersjoel237.koracore.infrastructure.provider;

import com.geekersjoel237.koracore.domain.port.ProviderPort;
import com.geekersjoel237.koracore.domain.vo.Amount;
import org.springframework.stereotype.Component;

/**
 * Placeholder adapter for the Mobile Money provider.
 * Replace with a real HTTP client implementation in later roadmap stages.
 */
@Component
public class MobileMoneyProviderAdapter implements ProviderPort {

    @Override
    public void credit(Amount amount, String paymentMethod) {
        // TODO Step 1: call external Mobile Money API to credit funds
        // Step 0: simulated provider — always succeeds
    }

    @Override
    public void debit(Amount amount, String paymentMethod) {
        // TODO Step 1: call external Mobile Money API to debit funds
        // Step 0: simulated provider — always succeeds
    }

    @Override
    public void send(Amount amount, String paymentMethod) {
        // TODO Step 1: call external Mobile Money API to send P2P funds
        // Step 0: simulated provider — always succeeds
    }
}