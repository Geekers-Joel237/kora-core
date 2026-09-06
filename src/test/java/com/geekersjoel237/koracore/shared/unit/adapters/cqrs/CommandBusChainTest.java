package com.geekersjoel237.koracore.shared.unit.adapters.cqrs;

import com.geekersjoel237.koracore.shared.adapters.in.cqrs.AntiReplayMiddleware;
import com.geekersjoel237.koracore.shared.adapters.in.cqrs.CorrelationIdMiddleware;
import com.geekersjoel237.koracore.shared.adapters.in.cqrs.RegisteredCommandBus;
import com.geekersjoel237.koracore.shared.adapters.in.cqrs.ValidationMiddleware;
import com.geekersjoel237.koracore.shared.adapters.in.cqrs.CommandRegistry;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.geekersjoel237.koracore.shared.application.cqrs.Command;
import com.geekersjoel237.koracore.shared.ports.in.CommandBus;
import com.geekersjoel237.koracore.shared.application.cqrs.CommandReplayedException;
import com.geekersjoel237.koracore.shared.application.cqrs.Middleware;
import com.geekersjoel237.koracore.shared.unit.doubles.MutableClock;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The chain, and the three things it must get right: the order, the refusals, and
 * what it writes to the log.
 */
class CommandBusChainTest {

    private static final Id CORRELATION = new Id("corr-abc");

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    /** What the registry answers when the chain lets the command through. */
    private static final String ANSWER = "handled";

    private static CommandRegistry registryAnswering() {
        return new CommandRegistry().register(SampleCommands.Sample.class, command -> ANSWER);
    }

    // ── order ─────────────────────────────────────────────────────────────────

    @Test
    void wraps_the_use_case_with_the_first_middleware_outermost() {
        List<String> trace = new ArrayList<>();
        CommandRegistry registry = new CommandRegistry()
                .register(SampleCommands.Sample.class, command -> {
                    trace.add("use case");
                    return ANSWER;
                });

        CommandBus bus = new RegisteredCommandBus(registry,
                List.of(recording(trace, "outer"), recording(trace, "inner")));

        assertThat(bus.dispatch(SampleCommands.sample(CORRELATION))).isEqualTo(ANSWER);
        assertThat(trace).containsExactly(
                "outer in", "inner in", "use case", "inner out", "outer out");
    }

    @Test
    void a_middleware_that_refuses_never_reaches_the_use_case() {
        List<String> trace = new ArrayList<>();
        CommandRegistry registry = new CommandRegistry()
                .register(SampleCommands.Sample.class, command -> {
                    trace.add("use case");
                    return ANSWER;
                });
        Middleware refusing = new Middleware() {
            @Override
            public <R> R around(Command<R> command, Supplier<R> next) {
                throw new IllegalStateException("refused");
            }
        };

        assertThatThrownBy(() -> new RegisteredCommandBus(registry, List.of(refusing))
                .dispatch(SampleCommands.sample(CORRELATION)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(trace).isEmpty();
    }

    // ── correlation ───────────────────────────────────────────────────────────

    @Test
    void publishes_the_correlation_id_for_the_duration_of_the_dispatch_and_no_longer() {
        List<String> seenInside = new ArrayList<>();
        CommandRegistry registry = new CommandRegistry()
                .register(SampleCommands.Sample.class, command -> {
                    seenInside.add(MDC.get(CorrelationIdMiddleware.MDC_KEY));
                    return ANSWER;
                });

        CommandBus bus = new RegisteredCommandBus(registry, List.of(new CorrelationIdMiddleware()));
        bus.dispatch(SampleCommands.sample(CORRELATION));

        assertThat(seenInside).containsExactly("corr-abc");
        assertThat(MDC.get(CorrelationIdMiddleware.MDC_KEY))
                .describedAs("a leaked MDC entry would tag the next request with this one's id")
                .isNull();
    }

    /**
     * The one place tempted to render a command. It must name the type and stop
     * there: the log of a wallet backend is not where a PIN belongs.
     */
    @Test
    void logs_the_command_type_and_never_its_contents() {
        var logger = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(CorrelationIdMiddleware.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        Level original = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        try {
            new RegisteredCommandBus(registryAnswering(), List.of(new CorrelationIdMiddleware()))
                    .dispatch(SampleCommands.sample(CORRELATION));

            List<String> lines = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(lines)
                    .describedAs("the guard is worthless if nothing was logged at all")
                    .isNotEmpty()
                    .allSatisfy(line -> assertThat(line).contains("Sample"));
            assertThat(String.join("\n", lines))
                    .describedAs("no PIN, no amount, no customer in a log line")
                    .doesNotContain(SampleCommands.SECRET)
                    .doesNotContain("customer-1");
        } finally {
            logger.setLevel(original);
            logger.detachAppender(appender);
        }
    }

    // ── validation ────────────────────────────────────────────────────────────

    @Test
    void refuses_a_command_without_a_correlation_id() {
        CommandBus bus = new RegisteredCommandBus(registryAnswering(),
                List.of(new ValidationMiddleware(VALIDATOR)));

        assertThatThrownBy(() -> bus.dispatch(SampleCommands.sample(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sample")
                .hasMessageContaining("correlation id");
    }

    // ── replay ────────────────────────────────────────────────────────────────

    @Test
    void refuses_a_correlation_id_already_seen_inside_the_window() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-05T10:00:00Z"), ZoneOffset.UTC);
        CommandBus bus = new RegisteredCommandBus(registryAnswering(),
                List.of(new AntiReplayMiddleware(Duration.ofMinutes(5), clock)));

        assertThat(bus.dispatch(SampleCommands.sample(CORRELATION))).isEqualTo(ANSWER);

        assertThatThrownBy(() -> bus.dispatch(SampleCommands.sample(CORRELATION)))
                .isInstanceOf(CommandReplayedException.class)
                .hasMessageContaining("corr-abc");
    }

    @Test
    void accepts_a_different_correlation_id_carrying_the_very_same_payload() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-05T10:00:00Z"), ZoneOffset.UTC);
        CommandBus bus = new RegisteredCommandBus(registryAnswering(),
                List.of(new AntiReplayMiddleware(Duration.ofMinutes(5), clock)));

        bus.dispatch(SampleCommands.sample(new Id("corr-1")));

        assertThat(bus.dispatch(SampleCommands.sample(new Id("corr-2"))))
                .describedAs("a customer topping up twice for the same amount is not a replay")
                .isEqualTo(ANSWER);
    }

    @Test
    void forgets_a_correlation_id_once_the_window_has_passed() {
        var clock = MutableClock.at(Instant.parse("2026-09-05T10:00:00Z"));
        CommandBus bus = new RegisteredCommandBus(registryAnswering(),
                List.of(new AntiReplayMiddleware(Duration.ofMinutes(5), clock)));

        bus.dispatch(SampleCommands.sample(CORRELATION));
        clock.advance(Duration.ofMinutes(6));

        assertThat(bus.dispatch(SampleCommands.sample(CORRELATION)))
                .describedAs("the window is a window, not a permanent ban on an id")
                .isEqualTo(ANSWER);
    }

    // ── doubles ───────────────────────────────────────────────────────────────

    private static Middleware recording(List<String> trace, String name) {
        return new Middleware() {
            @Override
            public <R> R around(Command<R> command, Supplier<R> next) {
                trace.add(name + " in");
                R result = next.get();
                trace.add(name + " out");
                return result;
            }
        };
    }

}
