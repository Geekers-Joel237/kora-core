package com.geekersjoel237.koracore.domain.model;

import com.geekersjoel237.koracore.domain.enums.ResourceType;
import com.geekersjoel237.koracore.domain.model.vo.AccountType;
import com.geekersjoel237.koracore.domain.vo.Amount;
import com.geekersjoel237.koracore.domain.vo.Balance;
import com.geekersjoel237.koracore.domain.vo.Id;

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
                Balance.zero("XOF"), false);
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
        String idValue = accountId.value();
        String lastFour = idValue.substring(idValue.length() - 4).toUpperCase();
        return "ACC-" + datePart + "-" + lastFour;
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

    public Balance debit(Amount amount) {
        if (accountType.resourceType() == ResourceType.FLOAT_ACCOUNT) {
            // Float accounts are unbounded — no balance check
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