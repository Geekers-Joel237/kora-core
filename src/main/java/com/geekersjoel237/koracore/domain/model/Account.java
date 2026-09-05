package com.geekersjoel237.koracore.domain.model;

import com.geekersjoel237.koracore.domain.enums.ResourceType;
import com.geekersjoel237.koracore.domain.vo.AccountType;
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
     * Debits the given amount from this account.
     *
     * <p><strong>FLOAT_ACCOUNT behaviour (intentional design) :</strong>
     * For float accounts, this method is a no-op on the stored balance — it returns
     * the current balance unchanged and never throws {@code InsufficientFundsException}.
     * The float account represents the system's treasury and is considered unbounded:
     * it can always honour a debit regardless of its nominal balance.
     *
     * <p>As a consequence, {@code balance_amount} on the float account row in the
     * database will always be 0, even after thousands of cash-in operations.
     * <strong>Auditing the float account must go through the {@code Operation} entries
     * in the ledger</strong>, not through the stored balance.
     * See ADR-001 for the full rationale.
     *
     * <p>For {@code CUSTOMER_ACCOUNT}, the balance is updated and
     * {@code InsufficientFundsException} is thrown if funds are insufficient.
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