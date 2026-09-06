package com.geekersjoel237.koracore.payment.domain.model;

import com.geekersjoel237.koracore.payment.domain.enums.LedgerEntryType;
import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.payment.domain.exception.SelfTransferException;
import com.geekersjoel237.koracore.payment.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

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
    private final Id fromAccountId;
    private final Id toAccountId;
    private final TransactionType type;
    private final PaymentMethod paymentMethod;
    private final Amount amount;
    private final Instant createdAt;
    private final List<LedgerEntry> entries;
    private final List<TrxStateHistoric> history;
    private TransactionState state;

    private Transaction(Id transactionId, String transactionNumber, Id fromAccountId, Id toAccountId,
                        TransactionType type, PaymentMethod paymentMethod, Amount amount) {
        this.transactionId = transactionId;
        this.transactionNumber = transactionNumber;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.state = TransactionState.INITIALIZED;
        this.type = type;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.createdAt = Instant.now();
        this.entries = new ArrayList<>();
        this.history = new ArrayList<>();
        this.history.add(TrxStateHistoric.of(transactionId, null, TransactionState.INITIALIZED));
    }

    public static Transaction create(Id transactionId,
                                     Id fromAccountId, Id toAccountId, TransactionType type,
                                     PaymentMethod paymentMethod, Amount amount) {
        if (transactionId == null) throw new IllegalArgumentException("Transaction id cannot be null");
        if (fromAccountId == null) throw new IllegalArgumentException("Transaction fromAccountId cannot be null");
        if (toAccountId == null) throw new IllegalArgumentException("Transaction toAccountId cannot be null");
        if (amount == null) throw new IllegalArgumentException("Transaction amount cannot be null");

        if (fromAccountId.equals(toAccountId))
            throw new SelfTransferException("Cannot transfer to the same account");

        var transactionNumber = generateTransactionNumber(transactionId);
        return new Transaction(transactionId, transactionNumber, fromAccountId, toAccountId,
                type, paymentMethod, amount);
    }

    public static Transaction createFromSnapshot(Snapshot snap,
                                                 List<LedgerEntry> entries,
                                                 List<TrxStateHistoric> history) {
        Transaction tx = new Transaction(
                snap.transactionId(), snap.transactionNumber(),
                snap.fromAccountId(), snap.toAccountId(),
                snap.type(), snap.paymentMethod(), snap.amount());
        tx.state = snap.state();
        tx.entries.clear();
        tx.entries.addAll(entries);
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
        this.entries.add(
                LedgerEntry.create(Id.generate(), LedgerEntryType.DEBIT, amount, debitAccountId));
        this.entries.add(
                LedgerEntry.create(Id.generate(), LedgerEntryType.CREDIT, amount, creditAccountId));
        verifyDoubleEntry();
    }

    private void verifyDoubleEntry() {
        Amount debit  = sumByType(LedgerEntryType.DEBIT);
        Amount credit = sumByType(LedgerEntryType.CREDIT);
        if (!debit.equals(credit))
            throw new IllegalStateException(
                    "Double-entry invariant violated: debit=" + debit.value()
                    + " credit=" + credit.value());
    }

    private Amount sumByType(LedgerEntryType type) {
        return this.entries.stream()
                .filter(op -> op.snapshot().type() == type)
                .map(op -> op.snapshot().amount())
                .reduce(Amount.of(BigDecimal.ZERO, this.amount.currency()), Amount::add);
    }

    private void transitionTo(TransactionState newState) {
        TransactionState old = this.state;
        this.state = this.state.transitionTo(newState);
        this.history.add(TrxStateHistoric.of(this.transactionId, old, this.state));
    }

    public List<LedgerEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public List<TrxStateHistoric> history() {
        return Collections.unmodifiableList(history);
    }

    public Snapshot snapshot() {
        return new Snapshot(
                transactionId, transactionNumber, fromAccountId, toAccountId,
                state, type, paymentMethod, amount, createdAt,
                entries.stream().map(LedgerEntry::snapshot).toList(),
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
            Id fromAccountId,
            Id toAccountId,
            TransactionState state,
            TransactionType type,
            PaymentMethod paymentMethod,
            Amount amount,
            Instant createdAt,
            List<LedgerEntry.Snapshot> entries,
            List<TrxStateHistoric> history
    ) {
    }
}