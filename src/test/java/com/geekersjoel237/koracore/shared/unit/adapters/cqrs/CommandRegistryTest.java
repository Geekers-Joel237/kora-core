package com.geekersjoel237.koracore.shared.unit.adapters.cqrs;

import com.geekersjoel237.koracore.shared.adapters.in.cqrs.RegisteredCommandBus;
import com.geekersjoel237.koracore.shared.adapters.in.cqrs.CommandRegistry;
import com.geekersjoel237.koracore.shared.adapters.in.cqrs.CommandRegistryVerifier;
import com.geekersjoel237.koracore.shared.application.cqrs.Command;
import com.geekersjoel237.koracore.shared.application.cqrs.UnregisteredCommandException;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A command whose use case was never registered must stop the application from
 * starting, not the customer's payment.
 *
 * <p>Nothing else catches it. Adding a command and forgetting its line in
 * {@code BusConfiguration} compiles, deploys, and answers requests — until the one
 * endpoint that dispatches it returns a 500 in production.
 */
class CommandRegistryTest {

    /**
     * Dispatch is reached through the bus, not called directly: {@code dispatch} is
     * package-private, and widening it so a test in another package could reach it
     * would trade a production boundary for a test's convenience. With no middlewares
     * the bus adds nothing, so this is the registry's behaviour and only that.
     */
    private static <R> R dispatch(CommandRegistry registry, Command<R> command) {
        return new RegisteredCommandBus(registry, List.of()).dispatch(command);
    }

    @Test
    void dispatches_to_the_use_case_registered_for_the_command() {
        var registry = new CommandRegistry()
                .register(SampleCommands.Unregistered.class, command -> "handled " + command.correlationId().value());

        assertThat(dispatch(registry, new SampleCommands.Unregistered(new Id("corr-1"))))
                .isEqualTo("handled corr-1");
    }

    @Test
    void refuses_a_command_it_does_not_know() {
        assertThatThrownBy(() -> dispatch(new CommandRegistry(),
                new SampleCommands.Unregistered(new Id("corr-1"))))
                .isInstanceOf(UnregisteredCommandException.class)
                .hasMessageContaining("Unregistered");
    }

    @Test
    void refuses_to_register_the_same_command_twice() {
        var registry = new CommandRegistry().register(SampleCommands.Unregistered.class, command -> "first");

        assertThatThrownBy(() -> registry.register(SampleCommands.Unregistered.class, command -> "second"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("registered twice");
    }

    // ── the startup guard ─────────────────────────────────────────────────────

    @Test
    void startup_fails_when_a_declared_command_has_no_use_case() {
        Set<Class<?>> declared = Set.of(SampleCommands.Sample.class, SampleCommands.Another.class,
                SampleCommands.Unregistered.class);
        Set<Class<?>> registered = Set.of(SampleCommands.Sample.class, SampleCommands.Another.class);

        assertThatThrownBy(() -> CommandRegistryVerifier.verify(declared, registered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unregistered")
                .hasMessageContaining("BusConfiguration");
    }

    /**
     * The failure mode a startup guard must not have: a scan that finds nothing
     * would let every command through unchecked, and the check would report success.
     */
    @Test
    void startup_fails_when_the_scan_finds_no_command_at_all() {
        assertThatThrownBy(() -> CommandRegistryVerifier.verify(Set.of(), Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("scan is misconfigured");
    }

    @Test
    void startup_passes_when_every_declared_command_is_registered() {
        Set<Class<?>> both = Set.of(SampleCommands.Sample.class, SampleCommands.Another.class);

        CommandRegistryVerifier.verify(both, both);
    }
}
