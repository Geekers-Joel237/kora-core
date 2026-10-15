package com.geekersjoel237.koracore.shared.unit.adapters.cqrs;

import com.geekersjoel237.koracore.shared.application.cqrs.Command;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;

import java.math.BigDecimal;

// Sample carries a secret on purpose: the log-redaction test needs something worth leaking.
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
