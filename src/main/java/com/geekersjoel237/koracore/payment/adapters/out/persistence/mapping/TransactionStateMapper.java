package com.geekersjoel237.koracore.payment.adapters.out.persistence.mapping;

import com.geekersjoel237.koracore.payment.domain.model.state.TransactionState;
import org.springframework.stereotype.Component;

@Component
public class TransactionStateMapper {

    public String toValue(TransactionState state) {
        return state == null ? null : state.name();
    }

    public TransactionState toState(String value) {
        return value == null ? null : TransactionState.fromValue(value);
    }
}
