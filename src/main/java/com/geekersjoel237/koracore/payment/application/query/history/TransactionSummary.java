package com.geekersjoel237.koracore.payment.application.query.history;

import com.geekersjoel237.koracore.payment.domain.enums.Direction;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;

import java.time.Instant;
import java.util.List;


public record TransactionSummary(
        String transactionId,
        String transactionNumber,
        TransactionType type,
        Direction direction,
        String state,
        Amount amount,
        PaymentMethod paymentMethod,
        String counterpart,          // null for CASH_IN / CASH_OUT; masked phone for P2P_TRANSFER
        Instant createdAt,
        List<StateEntry> stateHistory
) {

    public record StateEntry(String oldState, String newState, Instant occurredAt) {
    }
}