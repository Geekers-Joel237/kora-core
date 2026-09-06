package com.geekersjoel237.koracore.shared.config;

import com.geekersjoel237.koracore.shared.application.cqrs.Command;
import com.geekersjoel237.koracore.shared.ports.in.CommandBus;
import jakarta.validation.Validator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.geekersjoel237.koracore.shared.adapters.in.cqrs.CommandRegistrar;
import com.geekersjoel237.koracore.shared.adapters.in.cqrs.CommandRegistry;
import com.geekersjoel237.koracore.shared.adapters.in.cqrs.CommandRegistryVerifier;
import com.geekersjoel237.koracore.shared.adapters.in.cqrs.RegisteredCommandBus;
import com.geekersjoel237.koracore.shared.adapters.in.cqrs.CorrelationIdMiddleware;
import com.geekersjoel237.koracore.shared.adapters.in.cqrs.ValidationMiddleware;
import com.geekersjoel237.koracore.shared.adapters.in.cqrs.AntiReplayMiddleware;


@Configuration
public class BusConfiguration {


    private static final String SCAN_ROOT = "com.geekersjoel237.koracore";

    private static final String COMMAND_HOME = ".application.command";


    private static final Duration REPLAY_WINDOW = Duration.ofMinutes(5);

    private static Set<Class<?>> declaredCommands() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Command.class));

        Set<Class<?>> declared = new HashSet<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(SCAN_ROOT)) {
            String name = definition.getBeanClassName();
            if (name == null || !name.substring(0, name.lastIndexOf('.')).endsWith(COMMAND_HOME))
                continue;
            try {
                declared.add(Class.forName(name));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Scanned a command that cannot be loaded: " + name, e);
            }
        }
        return declared;
    }

    @Bean
    CommandRegistry commandRegistry(List<CommandRegistrar> registrars) {
        CommandRegistry registry = new CommandRegistry();
        registrars.forEach(registrar -> registrar.registerInto(registry));

        // Fails the context, not the first request.
        CommandRegistryVerifier.verify(declaredCommands(), registry.registeredTypes());
        return registry;
    }

    @Bean
    CommandBus commandBus(CommandRegistry registry, Validator validator, Clock clock) {
        return new RegisteredCommandBus(registry, List.of(
                new CorrelationIdMiddleware(),
                new ValidationMiddleware(validator),
                new AntiReplayMiddleware(REPLAY_WINDOW, clock)));
    }
}
