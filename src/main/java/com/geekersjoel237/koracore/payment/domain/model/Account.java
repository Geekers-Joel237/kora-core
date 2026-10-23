package com.geekersjoel237.koracore.payment.domain.model;

import com.geekersjoel237.koracore.payment.domain.enums.ResourceType;
import com.geekersjoel237.koracore.payment.domain.vo.AccountType;
import com.geekersjoel237.koracore.payment.domain.vo.Balance;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Account {

    private final Id accountId;
    private final String accountNumber;
    private final AccountType accountType;
    private Balance balance;
    private boolean isBlocked;

    private Account(Id accountId, String accountNumber, AccountType accountType,
                    Balance balance, boolean isBlocked) {
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.balance = balance;
        this.isBlocked = isBlocked;
    }

    private Account(Id accountId, AccountType accountType) {
        this(accountId, generateAccountNumber(accountId), accountType,
                Balance.zero("XAF"), false);
    }

    public static Account createCustomerAccount(Id accountId, Id customerId) {
        return new Account(accountId, AccountType.customer(customerId));
    }

    public static Account createFloatAccount(Id accountId, Id providerId) {
        return new Account(accountId, AccountType.float_(providerId));
    }

    public static Account createFromSnapshot(Snapshot snapshot) {
        return new Account(snapshot.accountId(), snapshot.accountNumber(),
                snapshot.accountType(), snapshot.balance(), snapshot.isBlocked());
    }

    private static String generateAccountNumber(Id accountId) {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String idClean = accountId.value().replace("-", ""); // 32 hex chars
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

    public Balance credit(Amount amount) {
        this.balance = this.balance.credit(amount);
        return this.balance;
    }

    /**
     * No-op for a FLOAT_ACCOUNT: the float is unbounded, so its stored balance stays 0
     * and is audited through the ledger entries instead (ADR-001).
     */
    public Balance debit(Amount amount) {
        if (accountType.resourceType() == ResourceType.FLOAT_ACCOUNT) {
            return this.balance;
        }
        this.balance = this.balance.debit(amount);
        return this.balance;
    }

    public Snapshot snapshot() {
        return new Snapshot(accountId, accountNumber, accountType, balance, isBlocked);
    }

    public record Snapshot(
            Id accountId,
            String accountNumber,
            AccountType accountType,
            Balance balance,
            boolean isBlocked
    ) {
    }
}