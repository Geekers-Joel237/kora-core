package com.geekersjoel237.koracore.domain.model;

import com.geekersjoel237.koracore.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.domain.enums.ResourceType;
import com.geekersjoel237.koracore.domain.enums.TransactionType;
import com.geekersjoel237.koracore.domain.exception.InsufficientFundsException;
import com.geekersjoel237.koracore.domain.exception.InvalidAccountException;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;

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

    // ── Legacy full-cycle methods (used by LedgerTest) ────────────────────────
    // These create a Transaction WITH operations in one shot.
    // Not used by the authorize/capture payment flow.

    public Transaction cashIn(Account customerAccount, Account floatAccount,
                              Amount amount, PaymentMethod paymentMethod) {
        requireActive(customerAccount, "Customer account is not active");
        requireActive(floatAccount, "Float account is not active");
        requirePositive(amount);

        Transaction tx = Transaction.create(
                Id.generate(),
                floatAccount.snapshot().accountId(),
                customerAccount.snapshot().accountId(),
                TransactionType.CASH_IN,
                paymentMethod,
                amount);

        tx.recordDoubleEntry(amount,
                floatAccount.snapshot().accountId(),
                customerAccount.snapshot().accountId());
        return tx;
    }

    public Transaction cashOut(Account customerAccount, Account floatAccount,
                               Amount amount, PaymentMethod paymentMethod) {
        requireActive(customerAccount, "Customer account is not active");
        requireActive(floatAccount, "Float account is not active");
        requirePositive(amount);
        requireSufficientFunds(customerAccount, amount);

        Transaction tx = Transaction.create(
                Id.generate(),
                customerAccount.snapshot().accountId(),
                floatAccount.snapshot().accountId(),
                TransactionType.CASH_OUT,
                paymentMethod,
                amount);

        tx.recordDoubleEntry(amount,
                customerAccount.snapshot().accountId(),
                floatAccount.snapshot().accountId());
        return tx;
    }

    public Transaction transfer(Account accountFrom, Account accountTo,
                                Amount amount, PaymentMethod paymentMethod) {
        // SelfTransferException is now enforced in Transaction.create()
        requireActive(accountFrom, "Sender account is not active");
        requireActive(accountTo, "Receiver account is not active");
        requireSufficientFunds(accountFrom, amount);

        Transaction tx = Transaction.create(
                Id.generate(),
                accountFrom.snapshot().accountId(),
                accountTo.snapshot().accountId(),
                TransactionType.P2P_TRANSFER,
                paymentMethod,
                amount);

        tx.recordDoubleEntry(amount,
                accountFrom.snapshot().accountId(),
                accountTo.snapshot().accountId());
        return tx;
    }

    // ── Two-phase payment flow ─────────────────────────────────────────────────

    /**
     * Creates a Transaction shell WITHOUT ledger operations.
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
        requirePositive(amount);

        // Transaction.create() FIRST — SelfTransferException wins over all other checks
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
        tx.recordDoubleEntry(amount, tx.snapshot().toId(), tx.snapshot().fromId());
        return tx;
    }

    public Snapshot snapshot() {
        return new Snapshot(ledgerId);
    }

    public record Snapshot(Id ledgerId) {
    }
}
