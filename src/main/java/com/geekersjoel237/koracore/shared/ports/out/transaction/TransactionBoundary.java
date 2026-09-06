package com.geekersjoel237.koracore.shared.ports.out.transaction;

import com.geekersjoel237.koracore.shared.application.transaction.ConcurrentUpdateException;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;

import java.util.function.Supplier;

/**
 * Runs a unit of work inside one transaction, and undoes it if the work refuses.
 *
 * <p><strong>This is not a Unit of Work.</strong> Nothing is tracked, queued or flushed
 * here: the work writes through repositories as it goes, and the boundary only decides
 * whether those writes become permanent.
 *
 * <h2>How work is undone</h2>
 *
 * <p>By throwing. There is no {@code setRollbackOnly}, no status object, and no way to
 * ask for a rollback while returning normally — an implementation rolls back when, and
 * only when, the supplier throws:
 *
 * <ul>
 *   <li>{@link BusinessException} and its subtypes — the domain refused. The writes so
 *       far are discarded and the exception reaches the caller unchanged. This is the
 *       intended way for a use case to abort: a rule fails, the aggregate throws, and
 *       nothing it had already written survives.</li>
 *   <li>Any other {@link RuntimeException} or {@link Error} — same rollback. A bug is
 *       not a reason to commit half a transfer.</li>
 *   <li>{@link ConcurrentUpdateException} — raised by the implementation itself when
 *       the transaction lost a race. The rollback has already happened by then; it is
 *       the commit that failed. Retryable, and the only thing here that is.</li>
 * </ul>
 *
 * <p>Returning normally commits. That is the whole contract, and it is why a use case
 * never has to reason about a transaction status: it either produces a result or it
 * throws.
 *
 * <h2>What must not happen inside</h2>
 *
 * <p>No network call, and in particular no provider call — a connection held across an
 * external I/O is what ADR-004 measured at p95 60 s and a 73.93 % error rate. A use
 * case that needs both splits into several boundaries with the I/O in between, and
 * accepts that the earlier ones are already committed. That is a saga, and it is why
 * the retry sits outside the boundary rather than inside it.
 */
@FunctionalInterface
public interface TransactionBoundary {

    /**
     * @param work the unit of work; committed if it returns, rolled back if it throws
     * @throws ConcurrentUpdateException when the transaction lost a race — an optimistic
     *         version clash or a deadlock. Both are retryable; nothing else this port
     *         throws is.
     */
    <T> T execute(Supplier<T> work);
}
