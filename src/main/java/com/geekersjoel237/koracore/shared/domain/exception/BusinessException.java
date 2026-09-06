package com.geekersjoel237.koracore.shared.domain.exception;

/**
 * The domain refused.
 *
 * <p>The root of every rule violation in the system: insufficient funds, an illegal
 * state transition, a blocked account. It says the request was understood and rejected,
 * which is what separates it from a bug and from
 * {@link com.geekersjoel237.koracore.shared.application.transaction.RetryExhaustedException},
 * where nothing was refused at all.
 *
 * <p>Throwing one inside
 * {@link com.geekersjoel237.koracore.shared.ports.out.transaction.TransactionBoundary#execute}
 * rolls the transaction back. That is deliberate and it is the only mechanism: a use
 * case aborts by letting the aggregate throw, never by asking a transaction status to
 * mark itself. Everything written before the throw is discarded.
 *
 * <p>Unchecked, because a caller that cannot honour a business rule has nothing useful
 * to do with it but let it travel to the edge, where a handler turns it into a status
 * code.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
