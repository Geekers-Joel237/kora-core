package com.geekersjoel237.koracore.domain.vo;

import com.geekersjoel237.koracore.domain.exception.InsufficientFundsException;

import java.math.BigDecimal;

public record Balance(Amount amount) {

    public Balance {
        if (amount == null)
            throw new IllegalArgumentException("Balance amount cannot be null");
    }

    public static Balance of(Amount amount) {
        return new Balance(amount);
    }

    public static Balance zero(String currency) {
        return new Balance(Amount.of(BigDecimal.ZERO, currency));
    }

    public Balance credit(Amount other) {
        return new Balance(this.amount.add(other));
    }

    public Balance debit(Amount other) {
        if (!this.amount.isGreaterThanOrEqual(other))
            throw new InsufficientFundsException(
                    "Insufficient funds: balance is " + this.amount.value()
                    + " " + this.amount.currency()
                    + ", tried to debit " + other.value()
                    + " " + other.currency());
        return new Balance(this.amount.subtract(other));
    }

    public Amount solde() {
        return this.amount;
    }
}