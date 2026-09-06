package com.geekersjoel237.koracore.shared.unit.doubles;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Remembers how each transaction ended.
 *
 * <p>{@link NoopTransactionManager} answers whether an exception propagates;
 * it cannot answer whether the work was undone, because its rollback does nothing.
 * That is the question that actually matters for a business failure, so it needs a
 * manager that keeps score.
 */
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
