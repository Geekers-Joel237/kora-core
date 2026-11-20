package com.geekersjoel237.koracore.shared.contract;

import com.geekersjoel237.koracore.shared.application.cqrs.Command;
import com.geekersjoel237.koracore.shared.ports.in.CommandBus;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class ProgrammableCommandBus implements CommandBus {

    private final Map<Class<?>, Function<Object, Object>> answers = new HashMap<>();
    private Command<?> lastDispatched;

    public <R, C extends Command<R>> void answer(Class<C> type, Function<C, R> answer) {
        answers.put(type, command -> answer.apply(type.cast(command)));
    }

    public <C extends Command<?>> void fail(Class<C> type, RuntimeException failure) {
        answers.put(type, command -> {
            throw failure;
        });
    }

    public <C extends Command<?>> C lastDispatched(Class<C> type) {
        return type.cast(lastDispatched);
    }

    public void reset() {
        answers.clear();
        lastDispatched = null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> R dispatch(Command<R> command) {
        lastDispatched = command;
        Function<Object, Object> answer = answers.get(command.getClass());
        if (answer == null)
            throw new AssertionError("No answer programmed for " + command.getClass().getSimpleName());
        return (R) answer.apply(command);
    }
}
