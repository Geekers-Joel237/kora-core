package com.geekersjoel237.koracore.shared.application.transaction;

/**
 * A transaction lost a race against another and can be retried as-is.
 *
 * <p>Raised by {@link com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary}
 * implementations, which translate their persistence technology's own failures into
 * it. The application retries on this type and on nothing else, so it never has to
 * name an ORM exception to decide.
 *
 * <p>Named <em>ConcurrentUpdate</em> rather than <em>ConcurrentModification</em> on
 * purpose: {@code java.util.ConcurrentModificationException} means something else
 * entirely, and a catch clause that confuses the two would silently retry a
 * collection bug.
 */
public class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(Throwable cause) {
        super("Transaction lost a concurrency race and may be retried", cause);
    }
}
