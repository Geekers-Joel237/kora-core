package com.geekersjoel237.koracore.payment.domain.model;

import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.ResourceType;
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

    private static void requireSufficientFunds(Account account, Amount amount) {
        if (!account.snapshot().balance().solde().isGreaterThanOrEqual(amount))
            throw new InsufficientFundsException(
                    "Insufficient funds: balance is "
                    + account.snapshot().balance().solde().value()
                    + ", required " + amount.value());
    }

    public Transaction initiate(Account fromAccount, Account toAccount,
                                TransactionType type, PaymentMethod paymentMethod,
                                Amount amount) {
        requireActive(fromAccount, "Source account is not active");
        requireActive(toAccount, "Destination account is not active");
        requirePositive(amount);

        if (fromAccount.snapshot().accountType().resourceType() != ResourceType.FLOAT_ACCOUNT) {
            requireSufficientFunds(fromAccount, amount);
        }
        return Transaction.create(
                Id.generate(),
                fromAccount.snapshot().accountId(),
                toAccount.snapshot().accountId(),
                type, paymentMethod, amount);
    }

    public void writeEntries(Transaction tx, Account fromAccount,
                             Account toAccount, Amount amount) {
        tx.recordDoubleEntry(amount,
                fromAccount.snapshot().accountId(),
                toAccount.snapshot().accountId());
    }

    public Transaction reverse(Transaction tx) {
        Amount amount = tx.snapshot().amount();
        tx.recordDoubleEntry(amount, tx.snapshot().toAccountId(), tx.snapshot().fromAccountId());
        return tx;
    }

    public Snapshot snapshot() {
        return new Snapshot(ledgerId);
    }

    public record Snapshot(Id ledgerId) {
    }
}
