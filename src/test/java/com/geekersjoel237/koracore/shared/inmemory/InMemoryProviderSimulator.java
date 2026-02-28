package com.geekersjoel237.koracore.shared.inmemory;

import com.geekersjoel237.koracore.domain.exception.ProviderException;
import com.geekersjoel237.koracore.domain.port.ProviderPort;
import com.geekersjoel237.koracore.domain.vo.Amount;

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
}