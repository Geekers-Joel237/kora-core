package com.geekersjoel237.koracore.shared.unit.doubles;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Fails at commit with a chosen exception, which is where a lost concurrency race
 * actually surfaces. Lets the boundary's translation be tested without a database
 * and without provoking a real deadlock.
 */
public class ThrowingTransactionManager implements PlatformTransactionManager {

    private final RuntimeException failure;

    public ThrowingTransactionManager(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public TransactionStatus getTransaction(TransactionDefinition def) {
        return new SimpleTransactionStatus(true);
    }

    @Override
    public void commit(TransactionStatus status) {
        throw failure;
    }

    @Override
    public void rollback(TransactionStatus status) {
    }
}
