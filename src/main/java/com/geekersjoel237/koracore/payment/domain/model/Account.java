package com.geekersjoel237.koracore.payment.domain.model;

import com.geekersjoel237.koracore.payment.domain.enums.ResourceType;
import com.geekersjoel237.koracore.payment.domain.exception.InsufficientFundsException;
import com.geekersjoel237.koracore.payment.domain.exception.InvalidAccountException;
import com.geekersjoel237.koracore.payment.domain.vo.AccountType;
import com.geekersjoel237.koracore.payment.domain.vo.Balance;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Account {

    private final Id accountId;
    private final String accountNumber;
    private final AccountType accountType;
    private Balance balance;
    private Amount reserved;
    private boolean isBlocked;

    private Account(Id accountId, String accountNumber, AccountType accountType,
                    Balance balance, Amount reserved, boolean isBlocked) {
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.balance = balance;
        this.reserved = reserved;
        this.isBlocked = isBlocked;
    }

    private Account(Id accountId, AccountType accountType) {
        this(accountId, generateAccountNumber(accountId), accountType,
                Balance.zero("XAF"), Amount.of(BigDecimal.ZERO, "XAF"), false);
    }

    public static Account createCustomerAccount(Id accountId, Id customerId) {
        return new Account(accountId, AccountType.customer(customerId));
    }

    public static Account createFloatAccount(Id accountId, Id providerId) {
        return new Account(accountId, AccountType.float_(providerId));
    }

    public static Account createFromSnapshot(Snapshot snapshot) {
        return new Account(snapshot.accountId(), snapshot.accountNumber(),
                snapshot.accountType(), snapshot.balance(), snapshot.reserved(), snapshot.isBlocked());
    }

    private static String generateAccountNumber(Id accountId) {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String idClean = accountId.value().replace("-", "");
        String suffix = idClean.substring(idClean.length() - 8).toUpperCase();
        return "ACC-" + datePart + "-" + suffix;
    }

    public boolean isActive() {
        return !isBlocked;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public void block() {
        this.isBlocked = true;
    }

    public boolean isFloat() {
        return accountType.resourceType() == ResourceType.FLOAT_ACCOUNT;
    }

    public Amount available() {
        return balance.solde().subtract(reserved);
    }

    public Balance credit(Amount amount) {
        if (isFloat())
            return balance;
        balance = balance.credit(amount);
        return balance;
    }

    public Balance debit(Amount amount) {
        if (isFloat())
            return balance;
        requireAvailable(amount);
        balance = balance.debit(amount);
        return balance;
    }

    public void reserve(Amount amount) {
        if (isFloat())
            throw new InvalidAccountException("A float account holds no reservation");
        requireAvailable(amount);
        reserved = reserved.add(amount);
    }

    public void release(Amount amount) {
        requireReserved(amount);
        reserved = reserved.subtract(amount);
    }

    public void debitReserved(Amount amount) {
        requireReserved(amount);
        reserved = reserved.subtract(amount);
        balance = balance.debit(amount);
    }

    private void requireAvailable(Amount amount) {
        if (!available().isGreaterThanOrEqual(amount))
            throw new InsufficientFundsException("Insufficient funds: available is "
                    + available().value() + ", required " + amount.value());
    }

    private void requireReserved(Amount amount) {
        if (!reserved.isGreaterThanOrEqual(amount))
            throw new IllegalStateException("Cannot settle " + amount.value()
                    + " against a reservation of " + reserved.value());
    }

    public Snapshot snapshot() {
        return new Snapshot(accountId, accountNumber, accountType, balance, reserved, isBlocked);
    }

    public record Snapshot(
            Id accountId,
            String accountNumber,
            AccountType accountType,
            Balance balance,
            Amount reserved,
            boolean isBlocked
    ) {
    }
}
