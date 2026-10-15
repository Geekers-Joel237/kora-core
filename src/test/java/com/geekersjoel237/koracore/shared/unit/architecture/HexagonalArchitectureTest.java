package com.geekersjoel237.koracore.shared.unit.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// Layers are located by path segment, never by a fixed directory, so every module is found by one expression.
class HexagonalArchitectureTest {

    private static final Path ROOT =
            Path.of("src", "main", "java", "com", "geekersjoel237", "koracore");

    /**
     * Every type neither JDK nor ours that application/ and ports/ may name. Empty on purpose;
     * an entry costs a line here and the sentence that justifies it. Compared as an equality,
     * so removing the last use of an entry fails too.
     */
    private static final Set<String> FRAMEWORK_ALLOW_LIST = Set.of();


    @Test
    void domain_names_no_framework_and_no_outer_layer() {
        assertNoImportContaining(filesUnder("domain"), "domain",
                "org.springframework.",
                "jakarta.persistence.",
                ".infrastructure.",
                ".adapters.");
        assertNoImportOfADrivingAdapter(filesUnder("domain"), "domain");
    }

    @Test
    void the_application_layer_names_no_framework() {
        assertNoImportContaining(frameworkFreeLayers(), "application and ports",
                "org.springframework.",
                "jakarta.persistence.",
                ".infrastructure.",
                ".adapters.");
        assertNoImportOfADrivingAdapter(frameworkFreeLayers(), "application and ports");
    }

    @Test
    void an_interactor_names_only_the_driving_port_it_implements() {
        List<String> offenders = interactors()
                .filter(file -> ourImports(file).stream()
                        .filter(line -> line.contains(".ports.in.")).count() > 1)
                .map(this::relative)
                .toList();

        assertThat(offenders)
                .describedAs("a use case is not a dependency of another use case")
                .isEmpty();
    }

    @Test
    void a_driving_port_declares_one_method() {
        List<String> oversized = drivingPorts()
                .filter(file -> declaredMethodCount(read(file)) > 1)
                .map(file -> relative(file) + " declares " + declaredMethodCount(read(file)) + " methods")
                .toList();

        assertThat(oversized).describedAs("one driving port, one use case").isEmpty();
    }

    @Test
    void every_command_has_a_port_that_handles_it() {
        List<String> unhandled = filesUnder("application", "command")
                .map(HexagonalArchitectureTest::declaredCommandName)
                .filter(java.util.Objects::nonNull)
                .filter(command -> drivingPorts()
                        .noneMatch(port -> read(port).contains("CommandHandler<" + command + ",")))
                .toList();

        assertThat(unhandled)
                .describedAs("a command with no CommandHandler port can only be bound by a lambda")
                .isEmpty();
    }

    /** The simple name of a type declaring {@code implements Command<...>}, or null. */
    private static String declaredCommandName(Path file) {
        String source = read(file);
        if (!source.contains("implements Command<")) return null;
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".java".length());
    }

    // ResetTestSupportAction is exempt: perf-profile tooling restoring the float after a truncate.
    @Test
    void no_aggregate_reaches_a_driving_adapter() {
        List<String> leaks = drivingAdapters()
                .filter(file -> !named(file, "ResetTestSupportAction"))
                .flatMap(file -> ourImports(file).stream()
                        .filter(line -> line.contains(".domain.model."))
                        .map(line -> relative(file) + "  ->  " + line))
                .toList();

        assertThat(leaks)
                .describedAs("driving adapters map result models, never aggregates")
                .isEmpty();
    }

    @Test
    void no_interactor_knows_the_bus() {
        List<String> coupled = interactors()
                .filter(file -> read(file).contains(".application.bus."))
                .map(this::relative)
                .toList();

        assertThat(coupled)
                .describedAs("the bus dispatches to a use case; the use case does not know it exists")
                .isEmpty();
    }

    @Test
    void transaction_template_lives_in_its_adapter_and_nowhere_else() {
        List<Path> holders = javaFilesUnder(ROOT)
                .filter(file -> read(file).contains("TransactionTemplate"))
                .toList();

        assertThat(holders)
                .describedAs("only the driven adapter may name Spring's TransactionTemplate")
                .singleElement()
                .satisfies(file -> assertThat(file.getFileName().toString())
                        .isEqualTo("SpringTransactionBoundary.java"));
    }


    @Test
    void the_application_layer_names_only_what_the_allow_list_permits() {
        Set<String> foreign = frameworkFreeLayers()
                .flatMap(file -> allImports(file).stream())
                .map(HexagonalArchitectureTest::importedType)
                .filter(type -> !type.startsWith("java.") && !type.startsWith("javax."))
                .filter(type -> !type.startsWith("com.geekersjoel237.koracore."))
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

        assertThat(foreign)
                .describedAs("a foreign type entered the application layer; "
                        + "add it to FRAMEWORK_ALLOW_LIST with the reason, or take it out")
                .isEqualTo(FRAMEWORK_ALLOW_LIST);
    }

    // Composition roots are exempt by construction: they live in config/, not application/usecases/.
    @Test
    void no_interactor_depends_on_another_interactor() {
        List<String> chained = interactors()
                .flatMap(file -> ourImports(file).stream()
                        .filter(line -> line.contains(".application.usecases."))
                        .map(line -> relative(file) + "  ->  " + line))
                .toList();

        assertThat(chained)
                .describedAs("a use case is composed by the configuration, never by another use case")
                .isEmpty();
    }

    // A ceiling, not an equality: the perf-profile support endpoints name no driving port.
    @Test
    void a_driving_adapter_depends_on_at_most_one_driving_port() {
        List<String> wide = drivingAdapters()
                .filter(file -> file.getFileName().toString().endsWith("Action.java"))
                .filter(file -> drivingPortImports(file).size() > 1)
                .map(file -> relative(file) + " names " + drivingPortImports(file))
                .toList();

        assertThat(wide)
                .describedAs("one endpoint, one use case")
                .isEmpty();
    }

    private List<String> drivingPortImports(Path file) {
        return ourImports(file).stream()
                .filter(line -> line.contains(".ports.in."))
                .toList();
    }

    /** {@code import a.b.C;} and {@code import static a.b.C.D;} both yield the type path. */
    private static String importedType(String importLine) {
        return importLine
                .replace("import static ", "")
                .replace("import ", "")
                .replace(";", "")
                .strip();
    }

    private void assertNoImportContaining(Stream<Path> layer, String name, String... forbidden) {
        List<String> violations = layer
                .flatMap(file -> allImports(file).stream()
                        .filter(line -> Stream.of(forbidden).anyMatch(line::contains))
                        .map(line -> relative(file) + "  ->  " + line))
                .toList();

        assertThat(violations).describedAs("forbidden imports in %s", name).isEmpty();
    }

    /**
     * Scoped to our own packages on purpose: a bare {@code .web.} would also catch
     * {@code org.springframework.web}, which is legitimate outside these layers.
     */
    private void assertNoImportOfADrivingAdapter(Stream<Path> layer, String name) {
        List<String> violations = layer
                .flatMap(file -> ourImports(file).stream()
                        .filter(line -> line.contains(".adapters.in."))
                        .map(line -> relative(file) + "  ->  " + line))
                .toList();

        assertThat(violations).describedAs("%s must not name a driving adapter", name).isEmpty();
    }

    /** Abstract method declarations in an interface body: a signature ending in ");". */
    private static int declaredMethodCount(String source) {
        return (int) source.lines()
                .map(String::strip)
                .filter(line -> line.endsWith(");"))
                .filter(line -> !line.startsWith("import "))
                .filter(line -> !line.startsWith("*") && !line.startsWith("//"))
                .count();
    }

    // Every locator is checked non-empty by every_layer_is_populated: a rule over an empty set passes silently.
    private Stream<Path> drivingAdapters() {
        return filesUnder("adapters", "in");
    }

    private Stream<Path> drivingPorts() {
        return filesUnder("ports", "in");
    }

    private Stream<Path> interactors() {
        return filesUnder("application", "usecases");
    }

    private Stream<Path> frameworkFreeLayers() {
        return filesUnderAny(new String[]{"application"}, new String[]{"ports"});
    }

    /**
     * The guard on the guards. A rule that scans an empty set reports success, so
     * every locator this class relies on must actually find something.
     */
    @Test
    void every_layer_is_populated() {
        assertThat(drivingAdapters().count()).describedAs("driving adapters").isPositive();
        assertThat(drivingPorts().count()).describedAs("driving ports").isPositive();
        assertThat(interactors().count()).describedAs("interactors").isPositive();
        assertThat(frameworkFreeLayers().count()).describedAs("application and ports").isPositive();
        assertThat(filesUnder("domain").count()).describedAs("domain").isPositive();
        assertThat(filesUnder("application", "command").count()).describedAs("commands").isPositive();
    }

    private Stream<Path> filesUnderAny(String[]... alternatives) {
        return javaFilesUnder(ROOT).filter(file -> {
            for (String[] segments : alternatives)
                if (hasSegments(file, segments)) return true;
            return false;
        });
    }

    private Stream<Path> filesUnder(String... segments) {
        return javaFilesUnder(ROOT).filter(file -> hasSegments(file, segments));
    }

    private boolean hasSegments(Path file, String[] segments) {
        List<String> parts = new ArrayList<>();
        ROOT.relativize(file).forEach(part -> parts.add(part.toString()));
        for (int start = 0; start + segments.length <= parts.size(); start++) {
            boolean matched = true;
            for (int offset = 0; offset < segments.length; offset++)
                if (!parts.get(start + offset).equals(segments[offset])) {
                    matched = false;
                    break;
                }
            if (matched) return true;
        }
        return false;
    }

    private boolean named(Path file, String simpleName) {
        return file.getFileName().toString().equals(simpleName + ".java");
    }

    private String relative(Path file) {
        return ROOT.relativize(file).toString();
    }

    private List<String> ourImports(Path file) {
        return allImports(file).stream()
                .filter(line -> line.startsWith("import com.geekersjoel237.koracore."))
                .toList();
    }

    private List<String> allImports(Path file) {
        return read(file).lines()
                .map(String::strip)
                .filter(line -> line.startsWith("import "))
                .toList();
    }

    private static Stream<Path> javaFilesUnder(Path directory) {
        try {
            return Files.walk(directory)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    // Collected eagerly: the walk's stream must close with the try block.
                    .toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk " + directory, e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }
}
