package com.geekersjoel237.koracore.shared.ports.out.transaction;

import com.geekersjoel237.koracore.shared.application.transaction.ConcurrentUpdateException;

import java.util.function.Supplier;

/**
 * Not a Unit of Work: the work writes through repositories as it goes. Returning commits;
 * any throw rolls back — there is no rollback status to set.
 *
 * <p>No network call inside: a connection held across provider I/O is what ADR-004
 * measured at p95 60 s and a 73.93 % error rate. Retry wraps the boundary, never the
 * reverse.
 */
@FunctionalInterface
public interface TransactionBoundary {

    /**
     * @throws ConcurrentUpdateException on an optimistic version clash or a deadlock,
     *         after rollback — the only retryable failure.
     */
    <T> T execute(Supplier<T> work);
}
