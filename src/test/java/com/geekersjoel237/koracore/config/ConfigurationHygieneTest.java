package com.geekersjoel237.koracore.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the configuration assignment rule (CONTRIBUTING.md section 3.4).
 * <p>
 * Two drifts are cheap to make and expensive to notice:
 * <ol>
 *   <li>giving a secret a fallback, so a clone without {@code .env} boots with a
 *       publicly readable signing key and says nothing;</li>
 *   <li>referencing an environment variable that {@code .env.example} never mentions,
 *       so the next person to clone the repository cannot start the application.</li>
 * </ol>
 * Plain JUnit — no Spring context, no database, no Docker.
 */
class ConfigurationHygieneTest {

    private static final Path MAIN_RESOURCES = Path.of("src", "main", "resources");
    private static final Path ENV_EXAMPLE = Path.of(".env.example");

    /** Property keys whose value must never be recoverable from the repository. */
    private static final Pattern SENSITIVE_KEY =
            Pattern.compile("(?i).*(secret|password|token|key).*");

    /** {@code ${VAR:fallback}} — the capture group is non-empty only when a fallback exists. */
    private static final Pattern PLACEHOLDER_WITH_FALLBACK =
            Pattern.compile("\\$\\{[A-Za-z0-9_.\\-]+:([^}]*)}");

    /** Any {@code ${VAR}} or {@code ${VAR:...}} whose name looks like an environment variable. */
    private static final Pattern ENV_PLACEHOLDER =
            Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)(?::[^}]*)?}");

    /**
     * @return why this line leaks a fallback for a sensitive property, or empty if it is clean.
     */
    static Optional<String> sensitiveFallback(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
            return Optional.empty();
        }
        int separator = indexOfSeparator(trimmed);
        if (separator < 0) {
            return Optional.empty();
        }
        String key = trimmed.substring(0, separator).strip();
        String value = trimmed.substring(separator + 1).strip();
        if (!SENSITIVE_KEY.matcher(key).matches()) {
            return Optional.empty();
        }
        Matcher matcher = PLACEHOLDER_WITH_FALLBACK.matcher(value);
        while (matcher.find()) {
            if (!matcher.group(1).isEmpty()) {
                return Optional.of(key + " declares the fallback '" + matcher.group(1) + "'");
            }
        }
        return Optional.empty();
    }

    private static int indexOfSeparator(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '=' || c == ':') return i;
            if (c == '$' || c == '{') return -1;
        }
        return -1;
    }

    @Test
    void no_sensitive_property_declares_a_fallback() {
        List<String> offenders = new ArrayList<>();
        for (Path file : configurationFiles()) {
            List<String> lines = readLines(file);
            for (int i = 0; i < lines.size(); i++) {
                int lineNumber = i + 1;
                sensitiveFallback(lines.get(i))
                        .ifPresent(reason -> offenders.add(file + ":" + lineNumber + "  →  " + reason));
            }
        }

        assertThat(offenders)
                .withFailMessage("""
                        A sensitive property declares a fallback value.
                        A fallback means a clone without .env starts anyway — with a credential
                        that is readable in the repository. Use ${VAR} with no default so the
                        boot fails instead, and put the example value in .env.example.
                        Offending lines:
                          %s""", String.join("\n  ", offenders))
                .isEmpty();
    }

    @Test
    void every_referenced_variable_is_documented_in_env_example() {
        Set<String> documented = envExampleKeys();
        Set<String> missing = new LinkedHashSet<>();

        for (Path file : configurationFiles()) {
            Matcher matcher = ENV_PLACEHOLDER.matcher(String.join("\n", readLines(file)));
            while (matcher.find()) {
                String variable = matcher.group(1);
                if (!documented.contains(variable)) {
                    missing.add(variable + "  (referenced in " + file + ")");
                }
            }
        }

        assertThat(missing)
                .withFailMessage("""
                        Environment variables are referenced but absent from .env.example.
                        Anyone cloning the repository would copy .env.example and fail to start.
                        Add them, with a non-functional example value for anything secret.
                        Missing:
                          %s""", String.join("\n  ", missing))
                .isEmpty();
    }

    @Test
    void the_scan_covers_at_least_one_configuration_file() {
        assertThat(configurationFiles())
                .withFailMessage("No configuration file found under %s — both guards would pass vacuously.",
                        MAIN_RESOURCES.toAbsolutePath())
                .isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "kora.security.jwt.secret=${JWT_SECRET:kora-core-default-secret-key-must-be-32-chars!}",
            "spring.datasource.password=${POSTGRES_PASSWORD:root}",
            "  spring.mail.password = ${MAIL_PASSWORD:hunter2}  ",
            "provider.api-key=${PROVIDER_API_KEY:sk-live-abc}",
            "some.access.token=${TOKEN:abc}",
            "SPRING_DATASOURCE_PASSWORD:${PG_PASSWORD:root}"
    })
    void detects_a_sensitive_fallback(String line) {
        assertThat(sensitiveFallback(line))
                .withFailMessage("'%s' should have been flagged.", line)
                .isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "kora.security.jwt.secret=${JWT_SECRET}",
            "spring.datasource.password=${POSTGRES_PASSWORD}",
            "spring.mail.password=${MAIL_PASSWORD:}",
            "# kora.security.jwt.secret=${JWT_SECRET:disabled-example}",
            "server.port=${SERVER_PORT:8081}",
            "spring.datasource.url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${POSTGRES_DB}",
            "spring.application.name=kora-core",
            "",
            "   "
    })
    void accepts_a_clean_line(String line) {
        assertThat(sensitiveFallback(line))
                .withFailMessage("'%s' should NOT have been flagged.", line)
                .isEmpty();
    }

    /** Both formats are scanned: the repository is YAML today, and .properties would win if reintroduced. */
    private static boolean isConfigurationFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".properties");
    }

    private static List<Path> configurationFiles() {
        assertThat(MAIN_RESOURCES)
                .withFailMessage("Resource directory not found at %s — tests must run from the project root.",
                        MAIN_RESOURCES.toAbsolutePath())
                .isDirectory();
        try (Stream<Path> paths = Files.walk(MAIN_RESOURCES)) {
            return paths.filter(Files::isRegularFile)
                    .filter(ConfigurationHygieneTest::isConfigurationFile)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot walk " + MAIN_RESOURCES.toAbsolutePath(), e);
        }
    }

    private static Set<String> envExampleKeys() {
        assertThat(ENV_EXAMPLE)
                .withFailMessage(".env.example not found at %s — it is the contract every clone relies on.",
                        ENV_EXAMPLE.toAbsolutePath())
                .isRegularFile();
        Set<String> keys = new LinkedHashSet<>();
        for (String line : readLines(ENV_EXAMPLE)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int equals = trimmed.indexOf('=');
            if (equals > 0) keys.add(trimmed.substring(0, equals).strip());
        }
        return keys;
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file.toAbsolutePath(), e);
        }
    }
}
