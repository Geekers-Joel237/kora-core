package com.geekersjoel237.koracore.shared.application.cqrs;


public class UnregisteredCommandException extends RuntimeException {

    public UnregisteredCommandException(Class<?> commandType) {
        super("No use case registered for command: " + commandType.getName());
    }
}
