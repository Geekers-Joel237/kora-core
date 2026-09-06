package com.geekersjoel237.koracore.shared.adapters.in.cqrs;

import com.geekersjoel237.koracore.shared.application.cqrs.Command;
import com.geekersjoel237.koracore.shared.ports.in.CommandBus;
import com.geekersjoel237.koracore.shared.application.cqrs.Middleware;

import java.util.List;
import java.util.function.Supplier;


public final class RegisteredCommandBus implements CommandBus {

    private final CommandRegistry registry;
    private final List<Middleware> middlewares;

    public RegisteredCommandBus(CommandRegistry registry, List<Middleware> middlewares) {
        this.registry = registry;
        this.middlewares = List.copyOf(middlewares);
    }

    @Override
    public <R> R dispatch(Command<R> command) {
        Supplier<R> chain = () -> registry.dispatch(command);

        // Wrapped back to front so that index 0 ends up outermost.
        for (int i = middlewares.size() - 1; i >= 0; i--) {
            Middleware middleware = middlewares.get(i);
            Supplier<R> next = chain;
            chain = () -> middleware.around(command, next);
        }
        return chain.get();
    }
}
