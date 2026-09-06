package com.geekersjoel237.koracore.shared.adapters.in.cqrs;

import com.geekersjoel237.koracore.shared.application.cqrs.Command;
import com.geekersjoel237.koracore.shared.application.cqrs.Middleware;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;


public final class ValidationMiddleware implements Middleware {

    private final Validator validator;

    public ValidationMiddleware(Validator validator) {
        this.validator = validator;
    }

    @Override
    public <R> R around(Command<R> command, Supplier<R> next) {
        if (command.correlationId() == null)
            throw new IllegalArgumentException(
                    command.getClass().getSimpleName() + " carries no correlation id");

        Set<ConstraintViolation<Command<R>>> violations = validator.validate(command);
        if (!violations.isEmpty()) {
            Set<String> messages = new TreeSet<>();
            for (ConstraintViolation<Command<R>> violation : violations)
                messages.add(violation.getPropertyPath() + " " + violation.getMessage());
            throw new IllegalArgumentException(
                    command.getClass().getSimpleName() + " is invalid: " + messages);
        }

        return next.get();
    }
}
