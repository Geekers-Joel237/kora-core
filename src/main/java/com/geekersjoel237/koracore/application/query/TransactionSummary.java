package com.geekersjoel237.koracore.application.query;

import com.geekersjoel237.koracore.domain.enums.Direction;
import com.geekersjoel237.koracore.domain.enums.TransactionType;
import com.geekersjoel237.koracore.domain.vo.Amount;

import java.time.Instant;
import java.util.List;

/**
 * Read model produced by GetTransactionHistoryUseCase.
 * Not a domain entity — it is assembled for a specific query use case.
 */
public record TransactionSummary(
        String transactionId,
        String transactionNumber,
        TransactionType type,
        Direction direction,
        String state,
        Amount amount,
        String paymentMethod,
        String counterpart,          // null for CASH_IN / CASH_OUT; masked phone for P2P_TRANSFER
        Instant createdAt,
        List<StateEntry> stateHistory
) {
    /**
     * One entry in the state machine audit trail for this transaction.
     */
    public record StateEntry(String oldState, String newState, Instant occurredAt) {}
}