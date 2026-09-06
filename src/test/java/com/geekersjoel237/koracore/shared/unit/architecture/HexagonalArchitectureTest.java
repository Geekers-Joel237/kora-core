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

/**
 * Guards the dependency direction by reading the source, the way
 * {@code ConfigurationHygieneTest} guards the configuration contract. Plain JUnit —
 * no Spring, no database, no extra dependency.
 *
 * <p>Layers are located by <strong>path segment</strong>, never by a fixed directory.
 * Every rule here used to resolve {@code domain} or {@code web} at the root, and every
 * one of them broke the day the modules arrived — twice, silently the first time,
 * because a forbidden prefix that matches nothing passes without guarding anything.
 * {@code auth/domain}, {@code payment/domain} and any module added later are all found
 * by the same expression.
 */
class HexagonalArchitectureTest {

    private static final Path ROOT =
            Path.of("src", "main", "java", "com", "geekersjoel237", "koracore");

    /**
     * Every third-party type the application layer and the ports are allowed to name.
     *
     * <p>It is empty, and that is the finding rather than an oversight. ADR-004 left a
     * debt worded as "application must stop naming Spring's transaction manager"; a
     * grep for {@code TransactionTemplate} would have called that settled while
     * {@code @Service}, {@code @Transactional} and a handful of persistence exceptions
     * were still in the layer. An allow-list settles it differently: the rule collects
     * <em>everything</em> foreign the layer imports and compares the whole set, so the
     * debt is paid only while this list stays as written.
     *
     * <p>Foreign means neither the JDK nor us. Not "Spring" — a validation annotation
     * or a JSON binding would bind the layer to a library just as firmly, and each
     * arrived in this codebase before as something nobody thought of as a framework.
     *
     * <p>Adding an entry is allowed. It costs a line here and the sentence that
     * justifies it, which is the entire point: the layer stays framework-free by
     * decision rather than by accident, and a decision to loosen it is legible in the
     * diff instead of hidden in an import block.
     */
    private static final Set<String> FRAMEWORK_ALLOW_LIST = Set.of();


    /** The domain names the JDK and itself. Nothing else. */
    @Test
    void domain_names_no_framework_and_no_outer_layer() {
        assertNoImportContaining(filesUnder("domain"), "domain",
                "org.springframework.",
                "jakarta.persistence.",
                ".infrastructure.",
                ".adapters.");
        assertNoImportOfADrivingAdapter(filesUnder("domain"), "domain");
    }

    /**
     * The application layer no longer names how a transaction is opened, nor what an
     * ORM calls a lost race. That is the debt ADR-004 recorded and deferred.
     *
     * <p>Not one type, not one annotation. The question of how far "zero Spring"
     * reaches here was open for eight steps and is now closed: every interactor is a
     * plain class, and the two collaborators that still carried a stereotype are
     * declared in their module's configuration like everything else. A layer that
     * needs no framework to be instantiated is a layer a test instantiates in a line.
     */
    @Test
    void the_application_layer_names_no_framework() {
        assertNoImportContaining(frameworkFreeLayers(), "application and ports",
                "org.springframework.",
                "jakarta.persistence.",
                ".infrastructure.",
                ".adapters.");
        assertNoImportOfADrivingAdapter(frameworkFreeLayers(), "application and ports");
    }

    /**
     * A driving port is an entry point, not a dependency.
     *
     * <p>An interactor names exactly one: the one it implements. Naming a second means
     * one use case is calling another, which is how a transaction boundary ends up
     * nested inside a boundary that does not know about it, and how two use cases stop
     * being separately deployable, testable or replaceable.
     *
     * <p>This replaces a rule that watched for a single type, {@code AuthUseCase}.
     * That type was split into four in an earlier step, and the rule kept passing on a
     * string that could no longer appear anywhere — green, and guarding nothing.
     */
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

    /**
     * A driving port names one use case, so it declares one method. A port that
     * gathers five makes every adapter depend on four things it does not call.
     *
     * <p>No exemption left: auth was the last module with a port gathering seven, and
     * splitting it is what removed the exception this rule used to carry.
     */
    @Test
    void a_driving_port_declares_one_method() {
        List<String> oversized = drivingPorts()
                .filter(file -> declaredMethodCount(read(file)) > 1)
                .map(file -> relative(file) + " declares " + declaredMethodCount(read(file)) + " methods")
                .toList();

        assertThat(oversized).describedAs("one driving port, one use case").isEmpty();
    }

    /**
     * Every command has a use case that answers to the common contract.
     *
     * <p>Without it a command could be bound to an arbitrary lambda, and the shape of
     * a use case would be whatever its author felt like that day. With it, the binding
     * in a registrar is the use case itself, and the registry has nothing to hold but
     * the pair.
     *
     * <p>Queries are absent by construction: they declare no command, so nothing here
     * looks for a handler for them.
     */
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

    /**
     * An aggregate never reaches a driving adapter.
     *
     * <p>Handing {@code Transaction} to a controller lets it walk the domain — the
     * ledger entries, the state history — which is rule 10 of CLAUDE.md turned inside
     * out. A use case answers with a result model, and the adapter has nothing to walk.
     *
     * <p>Value objects stay allowed: {@code Amount} crosses in both directions and is
     * the shared language, not a way in. Only {@code domain.model} is closed.
     *
     * <p>{@code ResetTestSupportAction} is exempt: perf-profile tooling that puts the
     * float account back after a truncate. A real breach, kept visible rather than
     * silently tolerated.
     */
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

    /**
     * The bus is a driving adapter, not the way in.
     *
     * <p>An interactor that named it would only be reachable through it — and the TTL
     * scheduler drives its use case with no command at all, a test builds one in a
     * line, and a provider callback consumer will drive another tomorrow.
     */
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

    /**
     * One adapter holds the template, so there is exactly one place where the
     * translation of a lost race can be got wrong.
     */
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


    /**
     * The allow-list, applied. This is the rule that formally settles ADR-004: not the
     * disappearance of {@code TransactionTemplate} from the layer, which a grep could
     * confirm and a single new import could quietly undo.
     *
     * <p>An equality, not a subset — the same choice as the recorded couplings in
     * {@code ModuleBoundariesTest}. Removing the last use of an allowed type has to
     * fail too, otherwise the list only ever grows and stops describing anything.
     */
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

    /**
     * One use case never calls another.
     *
     * <p>Two of them chained share one caller's transaction boundary without either
     * knowing it, so the inner one commits work the outer one is about to roll back —
     * or worse, does not, and the failure is invisible. It also ends the property that
     * makes the layer worth having: a use case that names a second is no longer
     * instantiable, replaceable or testable on its own.
     *
     * <p>Composition roots are exempt by construction: they live in {@code config/},
     * not in {@code application/usecases/}, and naming every interactor is their job.
     */
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

    /**
     * An endpoint is one use case wide.
     *
     * <p>A driving adapter that names two driving ports is deciding between them, and
     * that decision is application logic sitting in a controller — CLAUDE.md rule 10,
     * arrived at from the other side. Splitting the endpoint is the fix; there is no
     * case where an {@code *Action} needs both.
     *
     * <p>The perf-profile support endpoints name none at all, which is why the
     * assertion is a ceiling and not an equality.
     */
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

    // ── rules expressed over a layer, wherever the modules put it ─────────────

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

    // ── locating layers, not directories ──────────────────────────────────────

    /**
     * A layer is a concept, and the three modules now express each one the same way.
     * The transitional shapes — {@code web/}, {@code application/port/in},
     * {@code infrastructure/} — are gone, so the rules speak one vocabulary again.
     *
     * <p>Every locator is checked for emptiness by {@code every_layer_is_populated}.
     * That is not decoration: naming a directory that no longer exists is how these
     * rules went green and silent twice, and a locator that finds nothing makes every
     * rule built on it pass without guarding anything.
     */
    private Stream<Path> drivingAdapters() {
        return filesUnder("adapters", "in");
    }

    private Stream<Path> drivingPorts() {
        return filesUnder("ports", "in");
    }

    private Stream<Path> interactors() {
        return filesUnder("application", "usecases");
    }

    /** The layers that must name no framework: the use cases and every boundary. */
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
