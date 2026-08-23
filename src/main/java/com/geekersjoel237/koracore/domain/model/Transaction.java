package com.geekersjoel237.koracore.domain.model;

import com.geekersjoel237.koracore.domain.enums.OperationType;
import com.geekersjoel237.koracore.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.domain.enums.TransactionType;
import com.geekersjoel237.koracore.domain.exception.SelfTransferException;
import com.geekersjoel237.koracore.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Transaction {

    private final Id transactionId;
    private final String transactionNumber;
    private final Id fromId;
    private final Id toId;
    private final TransactionType type;
    private final PaymentMethod paymentMethod;
    private final Amount amount;
    private final Instant createdAt;
    private final List<Operation> operations;
    private final List<TrxStateHistoric> history;
    private TransactionState state;

    private Transaction(Id transactionId, String transactionNumber, Id fromId, Id toId,
                        TransactionType type, PaymentMethod paymentMethod, Amount amount) {
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

    public static Transaction create(Id transactionId,
                                     Id fromId, Id toId, TransactionType type,
                                     PaymentMethod paymentMethod, Amount amount) {
        if (transactionId == null) throw new IllegalArgumentException("Transaction id cannot be null");
        if (fromId == null) throw new IllegalArgumentException("Transaction fromId cannot be null");
        if (toId == null) throw new IllegalArgumentException("Transaction toId cannot be null");
        if (amount == null) throw new IllegalArgumentException("Transaction amount cannot be null");

        if (fromId.equals(toId))
            throw new SelfTransferException("Cannot transfer to the same account");

        var transactionNumber = generateTransactionNumber(transactionId);
        return new Transaction(transactionId, transactionNumber, fromId, toId,
                type, paymentMethod, amount);
    }

    public static Transaction createFromSnapshot(Snapshot snap,
                                                 List<Operation> operations,
                                                 List<TrxStateHistoric> history) {
        Transaction tx = new Transaction(
                snap.transactionId(), snap.transactionNumber(),
                snap.fromId(), snap.toId(),
                snap.type(), snap.paymentMethod(), snap.amount());
        tx.state = snap.state();
        tx.operations.clear();
        tx.operations.addAll(operations);
        tx.history.clear();
        tx.history.addAll(history);
        return tx;
    }

    private static String generateTransactionNumber(Id txId) {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // Remove dashes from UUID (32 hex chars) and take the last 8 → 16^8 = 4.3B combinations.
        // 4-char suffix (65 536 values) caused birthday collisions at ~70 txns/run (~3.6% per run).
        String idClean = txId.value().replace("-", "");
        String suffix = idClean.substring(idClean.length() - 8).toUpperCase();
        return "TRX-" + datePart + "-" + suffix;
    }

    public void recordDoubleEntry(Amount amount, Id debitAccountId, Id creditAccountId) {
        this.operations.add(
                Operation.create(Id.generate(), OperationType.DEBIT, amount, debitAccountId));
        this.operations.add(
                Operation.create(Id.generate(), OperationType.CREDIT, amount, creditAccountId));
        verifyDoubleEntry();
    }

    private void verifyDoubleEntry() {
        Amount debit  = sumByType(OperationType.DEBIT);
        Amount credit = sumByType(OperationType.CREDIT);
        if (!debit.equals(credit))
            throw new IllegalStateException(
                    "Double-entry invariant violated: debit=" + debit.value()
                    + " credit=" + credit.value());
    }

    private Amount sumByType(OperationType type) {
        return this.operations.stream()
                .filter(op -> op.snapshot().type() == type)
                .map(op -> op.snapshot().amount())
                .reduce(Amount.of(BigDecimal.ZERO, this.amount.currency()), Amount::add);
    }

    private void transitionTo(TransactionState newState) {
        TransactionState old = this.state;
        this.state = this.state.transitionTo(newState);
        this.history.add(TrxStateHistoric.of(this.transactionId, old, this.state));
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

    public void authorize() {
        transitionTo(TransactionState.AUTHORIZED);
    }

    public void capture() {
        transitionTo(TransactionState.CAPTURED);
    }

    public void pendSettlement() {
        transitionTo(TransactionState.SETTLEMENT_PENDING);
    }

    public void settle() {
        transitionTo(TransactionState.SETTLED);
    }

    public void markCompleted() {
        transitionTo(TransactionState.COMPLETED);
    }

    public void markFailed() {
        transitionTo(TransactionState.FAILED);
    }

    public void failAuthorization() {
        transitionTo(TransactionState.AUTHORIZATION_FAILED);
    }

    public void failCapture() {
        transitionTo(TransactionState.CAPTURE_FAILED);
    }

    public void failSettlement() {
        transitionTo(TransactionState.SETTLEMENT_FAILED);
    }

    public void reverse() {
        transitionTo(TransactionState.REVERSED);
    }

    public record Snapshot(
            Id transactionId,
            String transactionNumber,
            Id fromId,
            Id toId,
            TransactionState state,
            TransactionType type,
            PaymentMethod paymentMethod,
            Amount amount,
            Instant createdAt,
            List<Operation.Snapshot> operations,
            List<TrxStateHistoric> history
    ) {
    }
}