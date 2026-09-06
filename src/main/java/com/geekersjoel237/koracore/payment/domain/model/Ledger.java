package com.geekersjoel237.koracore.payment.domain.model;

import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.ResourceType;
import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;
import com.geekersjoel237.koracore.payment.domain.exception.InsufficientFundsException;
import com.geekersjoel237.koracore.payment.domain.exception.InvalidAccountException;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.exception.CurrencyMismatchException;
import com.geekersjoel237.koracore.payment.domain.exception.SelfTransferException;

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
        // isGreaterThanOrEqual also enforces currency match → CurrencyMismatchException propagated
        if (!account.snapshot().balance().solde().isGreaterThanOrEqual(amount))
            throw new InsufficientFundsException(
                    "Insufficient funds: balance is "
                            + account.snapshot().balance().solde().value()
                            + ", required " + amount.value());
    }

    // ── Two-phase payment flow ─────────────────────────────────────────────────

    /**
     * Creates a Transaction shell WITHOUT ledger entries.
     * Operations are written later, at capture time, via {@link #writeEntries}.
     *
     * <p>Invariant priority:
     * <ol>
     *   <li>{@code Transaction.create()} fires first → {@code SelfTransferException}
     *       is the aggregate's invariant and has absolute priority.</li>
     *   <li>{@code requireSufficientFunds} fires after → operational constraint.</li>
     * </ol>
     */
    public Transaction initiate(Account fromAccount, Account toAccount,
                                TransactionType type, PaymentMethod paymentMethod,
                                Amount amount) {
        requireActive(fromAccount, "Source account is not active");
        requireActive(toAccount, "Destination account is not active");
        requirePositive(amount);

        // Transaction.create() FIRST — SelfTransferException wins over all other
        // checks. Keep the local: inlining it into the return moves this call
        // below requireSufficientFunds and silently flips the priority.
        Transaction tx = Transaction.create(
                Id.generate(),
                fromAccount.snapshot().accountId(),
                toAccount.snapshot().accountId(),
                type, paymentMethod, amount);

        // requireSufficientFunds AFTER — float accounts are unbounded (ADR-001)
        if (fromAccount.snapshot().accountType().resourceType() != ResourceType.FLOAT_ACCOUNT) {
            requireSufficientFunds(fromAccount, amount);
        }

        return tx; // NO recordDoubleEntry() — written at capture via writeEntries()
    }

    public void writeEntries(Transaction tx, Account fromAccount,
                             Account toAccount, Amount amount) {
        tx.recordDoubleEntry(amount,
                fromAccount.snapshot().accountId(),
                toAccount.snapshot().accountId());
    }

    public Transaction reverse(Transaction tx) {
        Amount amount = tx.snapshot().amount();
        // Reverse: DEBIT the original recipient, CREDIT the original sender
        tx.recordDoubleEntry(amount, tx.snapshot().toAccountId(), tx.snapshot().fromAccountId());
        return tx;
    }

    public Snapshot snapshot() {
        return new Snapshot(ledgerId);
    }

    public record Snapshot(Id ledgerId) {
    }
}
