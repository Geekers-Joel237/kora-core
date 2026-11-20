package com.geekersjoel237.koracore.shared.ports.out.transaction;

import com.geekersjoel237.koracore.shared.application.transaction.ConcurrentUpdateException;

import java.util.function.Supplier;

@FunctionalInterface
public interface TransactionBoundary {

    /**
     * @throws ConcurrentUpdateException on an optimistic version clash or a deadlock,
     *         after rollback — the only retryable failure.
     */
    <T> T execute(Supplier<T> work);
}
