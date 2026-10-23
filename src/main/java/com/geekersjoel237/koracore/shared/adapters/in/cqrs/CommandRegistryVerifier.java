package com.geekersjoel237.koracore.shared.adapters.in.cqrs;

import java.util.Set;
import java.util.TreeSet;


public final class CommandRegistryVerifier {

    private CommandRegistryVerifier() {
    }

    public static void verify(Set<Class<?>> declared, Set<Class<?>> registered) {
        if (declared.isEmpty())
            throw new IllegalStateException(
                    "No Command type found — the classpath scan is misconfigured");

        Set<String> unregistered = new TreeSet<>();
        for (Class<?> type : declared)
            if (!registered.contains(type))
                unregistered.add(type.getName());

        if (!unregistered.isEmpty())
            throw new IllegalStateException(
                    "Command declared but no use case registered for it: " + unregistered
                            + " — add it to BusConfiguration");
    }
}
