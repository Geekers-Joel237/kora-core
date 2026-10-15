package com.geekersjoel237.koracore.shared.application.transaction;

/**
 * The only exception the application retries on, so it never names an ORM exception.
 * Not called ConcurrentModification: a catch confusing it with
 * {@code java.util.ConcurrentModificationException} would retry a collection bug.
 */
public class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(Throwable cause) {
        super("Transaction lost a concurrency race and may be retried", cause);
    }
}
