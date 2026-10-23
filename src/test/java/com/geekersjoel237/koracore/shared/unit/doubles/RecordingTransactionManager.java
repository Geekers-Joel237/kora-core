package com.geekersjoel237.koracore.shared.unit.doubles;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

public class RecordingTransactionManager implements PlatformTransactionManager {

    private int started;
    private int committed;
    private int rolledBack;

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
        started++;
        return new SimpleTransactionStatus(true);
    }

    @Override
    public void commit(TransactionStatus status) {
        committed++;
    }

    @Override
    public void rollback(TransactionStatus status) {
        rolledBack++;
    }

    public int started() {
        return started;
    }

    public int committed() {
        return committed;
    }

    public int rolledBack() {
        return rolledBack;
    }
}
