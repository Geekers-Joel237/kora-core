package com.geekersjoel237.koracore.shared.unit.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the Flyway migration naming convention (CONTRIBUTING.md section 9).
 * <p>
 * Migrations are versioned by timestamp, not by sequence: two branches adding a
 * migration in parallel would otherwise both claim the next integer. This test
 * scans the real migration directory, so a file that drifts from the convention
 * fails {@code ./gradlew test} rather than surviving until someone notices.
 * <p>
 * Plain JUnit — no Spring context, no database, no Docker.
 */
class MigrationNamingConventionTest {

    private static final Path MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");

    /** Human-readable form of {@link #MIGRATION_FILE}, quoted in every failure message. */
    private static final String EXPECTED = "V<yyyyMMddHHmm>__snake_case_name.sql";

    private static final Pattern MIGRATION_FILE =
            Pattern.compile("^V(\\d{12})__[a-z0-9]+(?:_[a-z0-9]+)*\\.sql$");

    /**
     * {@code uuuu} (proleptic year) rather than {@code yyyy} (year-of-era): STRICT
     * resolution rejects a year-of-era that comes without an era field.
     */
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuuMMddHHmm").withResolverStyle(ResolverStyle.STRICT);

    /**
     * @return why {@code fileName} breaks the convention, or empty if it complies.
     */
    static Optional<String> violation(String fileName) {
        Matcher matcher = MIGRATION_FILE.matcher(fileName);
        if (!matcher.matches()) {
            return Optional.of("does not match " + EXPECTED);
        }
        String timestamp = matcher.group(1);
        try {
            TIMESTAMP.parse(timestamp);
        } catch (DateTimeParseException e) {
            return Optional.of("carries an impossible timestamp '" + timestamp + "'");
        }
        return Optional.empty();
    }

    @Test
    void every_migration_file_follows_the_timestamp_convention() {
        List<String> offenders = migrationFileNames().stream()
                .flatMap(name -> violation(name)
                        .map(reason -> name + "  →  " + reason)
                        .stream())
                .toList();

        assertThat(offenders)
                .withFailMessage("""
                        Migration files break the naming convention.
                        Expected: %s
                        Offending files:
                          %s
                        Generate a timestamp with:  date -u +%%Y%%m%%d%%H%%M
                        Never rename a migration already applied to a database — add a new one.""",
                        EXPECTED, String.join("\n  ", offenders))
                .isEmpty();
    }

    /**
     * Without this, deleting every migration would make the scan above pass vacuously.
     */
    @Test
    void the_migration_directory_is_not_empty() {
        assertThat(migrationFileNames())
                .withFailMessage("No migration found under %s — the convention test would pass vacuously.",
                        MIGRATION_DIR.toAbsolutePath())
                .isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "V202605270702__initial_schema.sql",
            "V202601010000__add_idempotency_keys.sql",
            "V202612312359__backfill_operations_currency.sql",
            "V202605270702__x.sql"
    })
    void accepts_a_compliant_name(String fileName) {
        assertThat(violation(fileName)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "V1__initial_schema.sql",                  // sequential version, the convention we left
            "202605270702__initial_schema.sql",        // missing V prefix
            "V20260527070__initial_schema.sql",        // 11 digits
            "V2026052707021__initial_schema.sql",      // 13 digits
            "V202605270702_initial_schema.sql",        // single underscore separator
            "V202605270702__initialSchema.sql",        // camelCase
            "V202605270702__Initial_Schema.sql",       // capitals
            "V202605270702__initial__schema.sql",      // doubled underscore inside the name
            "V202605270702__initial_schema_.sql",      // trailing underscore
            "V202605270702__.sql",                     // empty description
            "V202605270702__initial_schema.SQL",       // uppercase extension
            "V202605270702__initial_schema.txt",       // wrong extension
            "V202613270702__initial_schema.sql",       // month 13
            "V202605320702__initial_schema.sql",       // day 32
            "V202602300000__initial_schema.sql",       // 30 February
            "V202605272502__initial_schema.sql"        // hour 25
    })
    void rejects_a_non_compliant_name(String fileName) {
        assertThat(violation(fileName))
                .withFailMessage("'%s' should have been rejected but passed the convention check.", fileName)
                .isPresent();
    }

    private static List<String> migrationFileNames() {
        assertThat(MIGRATION_DIR)
                .withFailMessage("Migration directory not found at %s — tests must run from the project root.",
                        MIGRATION_DIR.toAbsolutePath())
                .isDirectory();
        try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + MIGRATION_DIR.toAbsolutePath(), e);
        }
    }
}
