package com.geekersjoel237.koracore.shared.unit.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the test tree honest about which level each test is on.
 *
 * <p>The directory a test sits in is a claim: <em>unit</em> means it runs in
 * milliseconds with no context, <em>integration</em> means it talks to a real Postgres,
 * <em>e2e</em> means it goes over HTTP. The claim is worth nothing if nothing checks
 * it — a {@code @SpringBootTest} quietly added to a class under {@code unit/} makes the
 * fast suite slow and nobody notices until the build takes four minutes.
 *
 * <p>These rules read the tree itself, so they answer for every test that exists rather
 * than for the ones someone remembered to look at.
 */
class TestPyramidTest {

    private static final Path TESTS =
            Path.of("src", "test", "java", "com", "geekersjoel237", "koracore");

    private static final Set<String> MODULES = Set.of("auth", "payment", "shared");
    private static final Set<String> LEVELS = Set.of("unit", "integration", "e2e");

    /**
     * The whole point of the layout: a reader looking for "the fast tests for payment"
     * has one place to look, and a test cannot hide outside the pyramid.
     */
    @Test
    void every_test_declares_a_level() {
        List<String> misplaced = javaFilesUnder(TESTS)
                .filter(file -> read(file).contains("@Test"))
                .filter(file -> !onSomeLevel(file))
                .map(TestPyramidTest::relative)
                .toList();

        assertThat(misplaced)
                .describedAs("a test class lives under <module>/{unit,integration,e2e}/…")
                .isEmpty();
    }

    @Test
    void a_module_has_the_three_levels_and_nothing_else() {
        List<String> strays = MODULES.stream()
                .flatMap(module -> childrenOf(TESTS.resolve(module)))
                .filter(path -> !LEVELS.contains(path.getFileName().toString()))
                .map(TestPyramidTest::relative)
                .toList();

        assertThat(strays)
                .describedAs("only unit, integration and e2e sit directly under a module")
                .isEmpty();
    }

    /**
     * A unit test that boots a Spring context is an integration test wearing the wrong
     * label. It is also the slowest possible way to assert what a plain constructor
     * call would have proved.
     */
    @Test
    void nothing_on_the_unit_level_starts_a_context() {
        List<String> offenders = filesOnLevel("unit")
                .filter(file -> mentionsAny(read(file),
                        "@SpringBootTest", "@DataJpaTest", "@Testcontainers", "PostgreSQLContainer"))
                .map(TestPyramidTest::relative)
                .toList();

        assertThat(offenders)
                .describedAs("unit tests run with no context, no container, no database")
                .isEmpty();
    }

    /**
     * Integration is where a repository meets a real schema. The moment a test speaks
     * HTTP it is exercising the whole application, which is the level above.
     */
    @Test
    void nothing_on_the_integration_level_starts_a_web_server() {
        List<String> offenders = filesOnLevel("integration")
                .filter(file -> mentionsAny(read(file),
                        "RANDOM_PORT", "DEFINED_PORT", "TestRestTemplate", "@LocalServerPort"))
                .map(TestPyramidTest::relative)
                .toList();

        assertThat(offenders)
                .describedAs("an integration test stops at the repository; HTTP belongs to e2e")
                .isEmpty();
    }

    /** A guard on the guards: an empty scan would pass every rule above in silence. */
    @Test
    void every_level_of_every_module_is_populated() {
        for (String module : MODULES)
            for (String level : LEVELS)
                assertThat(javaFilesUnder(TESTS.resolve(module).resolve(level)).findAny())
                        .describedAs(module + "/" + level + " holds at least one file")
                        .isPresent();
    }

    // ── reading the tree ──────────────────────────────────────────────────────

    private static boolean onSomeLevel(Path file) {
        String path = relative(file);
        return MODULES.stream().anyMatch(module ->
                LEVELS.stream().anyMatch(level -> path.startsWith(module + "/" + level + "/")));
    }

    /**
     * Skips this file. It names every annotation it forbids, so a rule that read its
     * own source would report itself and never anything else.
     */
    private static Stream<Path> filesOnLevel(String level) {
        return MODULES.stream()
                .flatMap(module -> javaFilesUnder(TESTS.resolve(module).resolve(level)))
                .filter(file -> !file.getFileName().toString().equals("TestPyramidTest.java"));
    }

    private static boolean mentionsAny(String source, String... needles) {
        for (String needle : needles)
            if (source.contains(needle)) return true;
        return false;
    }

    private static String relative(Path file) {
        return TESTS.relativize(file).toString().replace('\\', '/');
    }

    private static Stream<Path> childrenOf(Path directory) {
        if (!Files.isDirectory(directory)) return Stream.empty();
        try (Stream<Path> children = Files.list(directory)) {
            return children.toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Stream<Path> javaFilesUnder(Path directory) {
        if (!Files.isDirectory(directory)) return Stream.empty();
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(path -> path.toString().endsWith(".java")).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
