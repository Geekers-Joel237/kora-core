package com.geekersjoel237.koracore.payment.domain.enums;

import com.geekersjoel237.koracore.payment.domain.model.state.TransactionState;

import java.util.Arrays;
import java.util.Set;

import static com.geekersjoel237.koracore.payment.domain.model.state.TransactionState.*;

public enum CustomerStatus {
    PENDING(INITIALIZED, AUTHORIZED, UNKNOWN),
    // Settlement is between Kora and the provider: once captured, the customer's money has moved.
    COMPLETED(CAPTURED, SETTLEMENT_PENDING, SETTLED, TransactionState.COMPLETED, SETTLEMENT_FAILED),
    FAILED(TransactionState.FAILED, AUTHORIZATION_FAILED, CAPTURE_FAILED),
    REFUNDED(REVERSED);

    private final Set<TransactionState> states;

    CustomerStatus(TransactionState... states) {
        this.states = Set.of(states);
    }

    public Set<TransactionState> states() {
        return states;
    }

    public static CustomerStatus of(TransactionState state) {
        return Arrays.stream(values())
                .filter(status -> status.states.contains(state))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No customer status for state " + state.name()));
    }
}
