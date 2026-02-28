package com.geekersjoel237.koracore.domain.model;

import com.geekersjoel237.koracore.domain.enums.OperationType;
import com.geekersjoel237.koracore.domain.enums.TransactionType;
import com.geekersjoel237.koracore.domain.exception.InsufficientFundsException;
import com.geekersjoel237.koracore.domain.exception.InvalidAccountException;
import com.geekersjoel237.koracore.domain.exception.SelfTransferException;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Id;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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

    private static void verifyDoubleEntry(Transaction tx) {
        Amount debit = sumByType(tx, OperationType.DEBIT);
        Amount credit = sumByType(tx, OperationType.CREDIT);
        if (!debit.equals(credit))
            throw new IllegalStateException(
                    "Double-entry invariant violated: debit=" + debit.value()
                            + " credit=" + credit.value());
    }

    private static Amount sumByType(Transaction tx, OperationType type) {
        return tx.operations().stream()
                .filter(op -> op.snapshot().type() == type)
                .map(op -> op.snapshot().amount())
                .reduce(Amount.of(BigDecimal.ZERO, "XOF"), Amount::add);
    }

    private static String generateTransactionNumber(Id txId) {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String idValue = txId.value();
        String lastFour = idValue.substring(idValue.length() - 4).toUpperCase();
        return "TRX-" + datePart + "-" + lastFour;
    }

    public Transaction cashIn(Account customerAccount, Account floatAccount,
                              Amount amount, String paymentMethod) {
        requireActive(customerAccount, "Customer account is not active");
        requireActive(floatAccount, "Float account is not active");
        requirePositive(amount);

        Id txId = Id.generate();
        Transaction tx = Transaction.create(
                txId,
                generateTransactionNumber(txId),
                floatAccount.snapshot().accountId(),
                customerAccount.snapshot().accountId(),
                TransactionType.CASH_IN,
                paymentMethod,
                amount
        );

        tx.addOperation(Operation.create(Id.generate(), OperationType.DEBIT,
                amount, floatAccount.snapshot().accountId()));
        tx.addOperation(Operation.create(Id.generate(), OperationType.CREDIT,
                amount, customerAccount.snapshot().accountId()));

        verifyDoubleEntry(tx);
        return tx;
    }

    public Transaction cashOut(Account customerAccount, Account floatAccount,
                               Amount amount, String paymentMethod) {
        requireActive(customerAccount, "Customer account is not active");
        requireActive(floatAccount, "Float account is not active");
        requirePositive(amount);
        requireSufficientFunds(customerAccount, amount);

        Id txId = Id.generate();
        Transaction tx = Transaction.create(
                txId,
                generateTransactionNumber(txId),
                customerAccount.snapshot().accountId(),
                floatAccount.snapshot().accountId(),
                TransactionType.CASH_OUT,
                paymentMethod,
                amount
        );

        tx.addOperation(Operation.create(Id.generate(), OperationType.DEBIT,
                amount, customerAccount.snapshot().accountId()));
        tx.addOperation(Operation.create(Id.generate(), OperationType.CREDIT,
                amount, floatAccount.snapshot().accountId()));

        verifyDoubleEntry(tx);
        return tx;
    }

    public Transaction transfer(Account accountFrom, Account accountTo,
                                Amount amount, String paymentMethod) {
        if (accountFrom.snapshot().accountId().equals(accountTo.snapshot().accountId()))
            throw new SelfTransferException("Cannot transfer to the same account");

        requireActive(accountFrom, "Sender account is not active");
        requireActive(accountTo, "Receiver account is not active");
        requireSufficientFunds(accountFrom, amount);

        Id txId = Id.generate();
        Transaction tx = Transaction.create(
                txId,
                generateTransactionNumber(txId),
                accountFrom.snapshot().accountId(),
                accountTo.snapshot().accountId(),
                TransactionType.P2P_TRANSFER,
                paymentMethod,
                amount
        );

        tx.addOperation(Operation.create(Id.generate(), OperationType.DEBIT,
                amount, accountFrom.snapshot().accountId()));
        tx.addOperation(Operation.create(Id.generate(), OperationType.CREDIT,
                amount, accountTo.snapshot().accountId()));

        verifyDoubleEntry(tx);
        return tx;
    }

    public Snapshot snapshot() {
        return new Snapshot(ledgerId);
    }

    public record Snapshot(Id ledgerId) {
    }
}