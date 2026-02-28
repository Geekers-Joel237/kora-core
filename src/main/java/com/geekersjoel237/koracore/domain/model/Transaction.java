package com.geekersjoel237.koracore.domain.model;

import com.geekersjoel237.koracore.domain.enums.TransactionState;
import com.geekersjoel237.koracore.domain.enums.TransactionType;
import com.geekersjoel237.koracore.domain.exception.InvalidStateTransitionException;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Transaction {

    private final Id transactionId;
    private final String transactionNumber;
    private final Id fromId;
    private final Id toId;
    private final TransactionType type;
    private final String paymentMethod;
    private final Amount amount;
    private final Instant createdAt;
    private final List<Operation> operations;
    private final List<TrxStateHistoric> history;
    private TransactionState state;

    private Transaction(Id transactionId, String transactionNumber, Id fromId, Id toId,
                        TransactionType type, String paymentMethod, Amount amount) {
        this.transactionId = transactionId;
        this.transactionNumber = transactionNumber;
        this.fromId = fromId;
        this.toId = toId;
        this.state = TransactionState.INITIALIZED;
        this.type = type;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.createdAt = Instant.now();
        this.operations = new ArrayList<>();
        this.history = new ArrayList<>();
        this.history.add(TrxStateHistoric.of(transactionId, null, TransactionState.INITIALIZED));
    }

    public static Transaction create(Id transactionId, String transactionNumber,
                                     Id fromId, Id toId, TransactionType type,
                                     String paymentMethod, Amount amount) {
        if (transactionId == null) throw new IllegalArgumentException("Transaction id cannot be null");
        if (fromId == null) throw new IllegalArgumentException("Transaction fromId cannot be null");
        if (toId == null) throw new IllegalArgumentException("Transaction toId cannot be null");
        if (amount == null) throw new IllegalArgumentException("Transaction amount cannot be null");
        return new Transaction(transactionId, transactionNumber, fromId, toId,
                type, paymentMethod, amount);
    }


    public void addOperation(Operation op) {
        this.operations.add(op);
    }

    public void transitionTo(TransactionState newState) {
        boolean valid = switch (this.state) {
            case INITIALIZED -> newState == TransactionState.PENDING;
            case PENDING -> newState == TransactionState.COMPLETED
                    || newState == TransactionState.FAILED;
            case COMPLETED, FAILED -> false;
        };
        if (!valid)
            throw new InvalidStateTransitionException(this.state, newState);
        TransactionState old = this.state;
        this.state = newState;
        this.history.add(TrxStateHistoric.of(this.transactionId, old, newState));
    }

    public List<Operation> operations() {
        return Collections.unmodifiableList(operations);
    }

    public List<TrxStateHistoric> history() {
        return Collections.unmodifiableList(history);
    }


    public Snapshot snapshot() {
        return new Snapshot(
                transactionId, transactionNumber, fromId, toId,
                state, type, paymentMethod, amount, createdAt,
                operations.stream().map(Operation::snapshot).toList(),
                Collections.unmodifiableList(history)
        );
    }

    public record Snapshot(
            Id transactionId,
            String transactionNumber,
            Id fromId,
            Id toId,
            TransactionState state,
            TransactionType type,
            String paymentMethod,
            Amount amount,
            Instant createdAt,
            List<Operation.Snapshot> operations,
            List<TrxStateHistoric> history
    ) {
    }
}