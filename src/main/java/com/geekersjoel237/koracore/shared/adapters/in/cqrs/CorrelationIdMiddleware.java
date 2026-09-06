package com.geekersjoel237.koracore.shared.adapters.in.cqrs;

import com.geekersjoel237.koracore.shared.application.cqrs.Command;
import com.geekersjoel237.koracore.shared.application.cqrs.Middleware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.function.Supplier;


public final class CorrelationIdMiddleware implements Middleware {

    public static final String MDC_KEY = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdMiddleware.class);

    @Override
    public <R> R around(Command<R> command, Supplier<R> next) {
        MDC.put(MDC_KEY, command.correlationId().value());
        try {
            log.debug("dispatching {}", command.getClass().getSimpleName());
            return next.get();
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
