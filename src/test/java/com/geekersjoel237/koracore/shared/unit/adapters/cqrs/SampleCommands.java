package com.geekersjoel237.koracore.shared.unit.adapters.cqrs;

import com.geekersjoel237.koracore.shared.application.cqrs.Command;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;

import java.math.BigDecimal;

/**
 * Commands that exist only to be dispatched.
 *
 * <p>The bus knows nothing about payments, and these tests used {@code CashInCommand}
 * only because it was a command lying around — which made the kernel's own tests depend
 * on a module, and made a failure here read as if something about cash-in had broken.
 *
 * <p>{@link Sample} carries a secret and a customer id on purpose: the log-redaction
 * test needs something worth leaking.
 */
final class SampleCommands {

    static final String SECRET = "483920";

    private SampleCommands() {
    }

    static Sample sample(Id correlationId) {
        return new Sample(correlationId, new Id("customer-1"), Pin.of(SECRET),
                Amount.of(BigDecimal.valueOf(5000), "XAF"));
    }

    record Sample(Id correlationId, Id customerId, Pin secret, Amount amount)
            implements Command<String> {
    }

    record Another(Id correlationId) implements Command<String> {
    }

    record Unregistered(Id correlationId) implements Command<String> {
    }
}
