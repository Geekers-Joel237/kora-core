package com.geekersjoel237.koracore.shared.adapters.in.cqrs;

import com.geekersjoel237.koracore.shared.application.cqrs.Command;
import com.geekersjoel237.koracore.shared.application.cqrs.CommandReplayedException;
import com.geekersjoel237.koracore.shared.application.cqrs.Middleware;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;


public final class AntiReplayMiddleware implements Middleware {

    private final Map<String, Instant> seen = new ConcurrentHashMap<>();
    private final Duration window;
    private final Clock clock;

    public AntiReplayMiddleware(Duration window, Clock clock) {
        this.window = window;
        this.clock = clock;
    }

    @Override
    public <R> R around(Command<R> command, Supplier<R> next) {
        Instant now = clock.instant();
        forget(now);

        String key = command.correlationId().value();
        if (seen.putIfAbsent(key, now) != null)
            throw new CommandReplayedException(command.correlationId());

        return next.get();
    }


    private void forget(Instant now) {
        Instant cutoff = now.minus(window);
        seen.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }
}
