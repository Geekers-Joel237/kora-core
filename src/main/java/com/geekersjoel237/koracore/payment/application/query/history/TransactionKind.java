package com.geekersjoel237.koracore.payment.application.query.history;

import com.geekersjoel237.koracore.payment.domain.enums.TransactionType;

public enum TransactionKind {
    DEPOSIT, WITHDRAWAL, TRANSFER_SENT, TRANSFER_RECEIVED;

    public static TransactionKind of(TransactionType type, boolean leavesTheWallet) {
        return switch (type) {
            case CASH_IN -> DEPOSIT;
            case CASH_OUT -> WITHDRAWAL;
            case P2P_TRANSFER -> leavesTheWallet ? TRANSFER_SENT : TRANSFER_RECEIVED;
        };
    }

    public boolean leavesTheWallet() {
        return this == WITHDRAWAL || this == TRANSFER_SENT;
    }
}
