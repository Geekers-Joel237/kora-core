package com.geekersjoel237.koracore.shared.adapters.in.cqrs;

import com.geekersjoel237.koracore.shared.application.cqrs.Command;
import com.geekersjoel237.koracore.shared.ports.in.CommandHandler;
import com.geekersjoel237.koracore.shared.application.cqrs.UnregisteredCommandException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class CommandRegistry {

    private final Map<Class<?>, Function<Command<?>, Object>> handlers = new HashMap<>();

    public <R, C extends Command<R>> CommandRegistry register(Class<C> type, CommandHandler<C, R> useCase) {
        Function<Command<?>, Object> erased = command -> useCase.execute(type.cast(command));
        if (handlers.putIfAbsent(type, erased) != null)
            throw new IllegalStateException("Command registered twice: " + type.getName());
        return this;
    }

    @SuppressWarnings("unchecked")
    <R> R dispatch(Command<R> command) {
        Function<Command<?>, Object> useCase = handlers.get(command.getClass());
        if (useCase == null)
            throw new UnregisteredCommandException(command.getClass());
        return (R) useCase.apply(command);
    }

    public Set<Class<?>> registeredTypes() {
        return Set.copyOf(handlers.keySet());
    }
}
