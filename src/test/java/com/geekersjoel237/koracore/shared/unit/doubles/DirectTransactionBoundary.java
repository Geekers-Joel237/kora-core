package com.geekersjoel237.koracore.shared.unit.doubles;

import com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary;

import java.util.function.Supplier;

/**
 * Runs the work with no transaction at all — {@code work -> work.get()}.
 * The whole point of the port: a use case is testable without a transaction manager.
 */
public class DirectTransactionBoundary implements TransactionBoundary {

    @Override
    public <T> T execute(Supplier<T> work) {
        return work.get();
    }
}
