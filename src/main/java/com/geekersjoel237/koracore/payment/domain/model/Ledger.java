package com.geekersjoel237.koracore.payment.domain.model;

import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.payment.domain.exception.InsufficientFundsException;
import com.geekersjoel237.koracore.payment.domain.exception.InvalidAccountException;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

public class Ledger {

    private final Id ledgerId;

    private Ledger(Id ledgerId) {
        this.ledgerId = ledgerId;
    }

    public static Ledger create(Id ledgerId) {
        return new Ledger(ledgerId);
    }

    private static void requireActive(Account account, String message) {
        if (!account.isActive())
            throw new InvalidAccountException(message);
    }

    private static void requirePositive(Amount amount) {
        if (!amount.isStrictPositive())
            throw new IllegalArgumentException("Amount must be positive");
    }

    private static void requireAvailable(Account account, Amount amount) {
        if (!account.isFloat() && !account.available().isGreaterThanOrEqual(amount))
            throw new InsufficientFundsException("Insufficient funds: available is "
                    + account.available().value() + ", required " + amount.value());
    }

    private static void requireParty(Account account, Id expected, Transaction tx) {
        if (!account.snapshot().accountId().equals(expected))
            throw new IllegalArgumentException("Account " + account.snapshot().accountId().value()
                    + " is not a party to transaction " + tx.snapshot().transactionId().value());
    }

    private static void requireParties(Transaction tx, Account source, Account destination) {
        requireParty(source, tx.snapshot().fromAccountId(), tx);
        requireParty(destination, tx.snapshot().toAccountId(), tx);
    }

    public Transaction initiate(Account fromAccount, Account toAccount,
                                TransactionType type, PaymentMethod paymentMethod,
                                Amount amount) {
        requireActive(fromAccount, "Source account is not active");
        requireActive(toAccount, "Destination account is not active");
        requirePositive(amount);
        requireAvailable(fromAccount, amount);
        return Transaction.create(
                Id.generate(),
                fromAccount.snapshot().accountId(),
                toAccount.snapshot().accountId(),
                type, paymentMethod, amount);
    }

    public FundsHold hold(Transaction tx, Account source) {
        requireParty(source, tx.snapshot().fromAccountId(), tx);
        Amount amount = tx.snapshot().amount();
        source.reserve(amount);
        return FundsHold.place(tx.snapshot().transactionId(), source.snapshot().accountId(), amount);
    }

    public void post(Transaction tx, Account source, Account destination) {
        requireParties(tx, source, destination);
        Amount amount = tx.snapshot().amount();
        source.debit(amount);
        destination.credit(amount);
        tx.recordDoubleEntry(amount, source.snapshot().accountId(), destination.snapshot().accountId());
    }

    public void capture(Transaction tx, FundsHold hold, Account source, Account destination) {
        requireParties(tx, source, destination);
        if (!hold.snapshot().transactionId().equals(tx.snapshot().transactionId()))
            throw new IllegalArgumentException("Hold " + hold.snapshot().id().value()
                    + " does not belong to transaction " + tx.snapshot().transactionId().value());
        Amount amount = tx.snapshot().amount();
        hold.consume();
        source.debitReserved(amount);
        destination.credit(amount);
        tx.recordDoubleEntry(amount, source.snapshot().accountId(), destination.snapshot().accountId());
    }

    public void release(FundsHold hold, Account source) {
        if (!hold.snapshot().accountId().equals(source.snapshot().accountId()))
            throw new IllegalArgumentException("Hold " + hold.snapshot().id().value()
                    + " is not on account " + source.snapshot().accountId().value());
        hold.release();
        source.release(hold.snapshot().amount());
    }

    public Transaction reverse(Transaction tx, Account source, Account destination) {
        requireParties(tx, source, destination);
        Amount amount = tx.snapshot().amount();
        destination.debit(amount);
        source.credit(amount);
        tx.recordDoubleEntry(amount, destination.snapshot().accountId(), source.snapshot().accountId());
        return tx;
    }

    public Snapshot snapshot() {
        return new Snapshot(ledgerId);
    }

    public record Snapshot(Id ledgerId) {
    }
}
