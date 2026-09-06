package com.geekersjoel237.koracore.shared.unit.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two rules that make {@code auth}, {@code payment} and {@code shared} modules
 * rather than folders.
 *
 * <ol>
 *   <li>The kernel depends on nothing. Not "almost nothing" — nothing.</li>
 *   <li>Whatever the two business modules still owe each other is written down here,
 *       and cannot change without someone saying why.</li>
 * </ol>
 */
class ModuleBoundariesTest {

    private static final Path ROOT =
            Path.of("src", "main", "java", "com", "geekersjoel237", "koracore");

    /**
     * Tests count. A kernel test that reached into a module for an exception to throw
     * was how {@code shared} last named {@code payment} — invisible to a rule that
     * only read production sources, and just as much a dependency to anyone reading
     * the kernel to learn what it needs.
     *
     * <p>Two of the three levels, not three. {@code shared/e2e} holds the harness that
     * drives the whole application over HTTP: it registers a customer and opens their
     * wallet, so it names both modules by construction. That is what end-to-end means,
     * and a rule that forbade it would only be satisfied by pretending the harness
     * belongs to one module or by duplicating it into both.
     */
    private static final List<Path> KERNEL_TEST_ROOTS = List.of(
            Path.of("src", "test", "java", "com", "geekersjoel237", "koracore", "shared", "unit"),
            Path.of("src", "test", "java", "com", "geekersjoel237", "koracore", "shared", "integration"));

    private static final String BASE = "com.geekersjoel237.koracore.";
    private static final Set<String> MODULES = Set.of("auth", "payment", "shared");

    private static final Pattern OUR_IMPORT = Pattern.compile(
            "^import (?:static )?(" + Pattern.quote(BASE) + "[\\w.]+);", Pattern.MULTILINE);

    /**
     * What the two business modules still take from each other after phase A, and what
     * it would take to stop.
     *
     * <ul>
     *   <li><b>auth &rarr; payment</b> — {@code RegisterService} opens a wallet, and
     *       {@code LoginService} plus {@code CustomerPinVerifier} sit on either side of
     *       the PIN port. A published {@code OpenWalletUseCase} closes the first; the
     *       PIN port is working as intended and only its wiring shows here. The count
     *       rose when {@code AuthService} was split — the same coupling, now visible in
     *       the four classes that actually carry it.</li>
     *   <li><b>payment &rarr; auth</b> — a transfer resolves its recipient through
     *       {@code Customer}, and the provider needs the payer's phone. Looking a
     *       wallet up by msisdn directly closes most of it.</li>
     * </ul>
     *
     * <p>The assertion is an equality, not a subset: a new coupling fails the build,
     * and closing one <em>also</em> fails it, which is what forces this list to shrink
     * as phase B lands rather than being quietly forgotten.
     *
     * <p>Until it is empty, {@code auth} and {@code payment} are packages and not
     * modules: two of the entries below carry an aggregate across, and a package whose
     * aggregate the neighbour imports has a directory rather than a boundary. Roadmap
     * Étape 2 is open, and this list is the measure of how far from done it is.
     */
    private static final Map<String, Set<String>> RECORDED = new TreeMap<>(Map.of(
            "auth -> payment", Set.of(
                    "AuthUseCaseConfiguration", "CustomerPinVerifier",
                    "LoginService", "RegisterService"),
            "payment -> auth", Set.of(
                    "CashInService", "CashOutService", "MobileMoneyProviderAdapter",
                    "ProviderPort", "TransactionHistoryService",
                    "TransferService", "UseCaseConfiguration")));

    /**
     * A kernel is depended upon; it does not depend. One exception handler knowing
     * every module's failures, and one test endpoint bootstrapping another module's
     * aggregate, were enough to make {@code shared} the most coupled package in the
     * repository while looking like the least.
     */
    @Test
    void the_kernel_names_no_module() {
        Stream<Path> kernelSources = Stream.concat(
                javaFilesUnder(ROOT.resolve("shared")),
                KERNEL_TEST_ROOTS.stream().flatMap(ModuleBoundariesTest::javaFilesUnder));

        List<String> offenders = kernelSources
                .flatMap(file -> ourImports(file).stream()
                        .filter(imported -> {
                            String module = moduleOf(imported);
                            return module.equals("auth") || module.equals("payment");
                        })
                        .map(imported -> relative(file) + "  ->  " + imported))
                .toList();

        assertThat(offenders)
                .describedAs("shared/ is the kernel: it names auth and payment nowhere")
                .isEmpty();
    }

    @Test
    void the_business_modules_reach_into_each_other_exactly_where_recorded() {
        assertThat(actualCouplings())
                .describedAs("a coupling appeared or disappeared; update this list and say why")
                .isEqualTo(RECORDED);
    }

    // ── measurement ───────────────────────────────────────────────────────────

    private static Map<String, Set<String>> actualCouplings() {
        Map<String, Set<String>> found = new TreeMap<>();
        javaFilesUnder(ROOT).forEach(file -> {
            String from = moduleOfFile(file);
            if (!MODULES.contains(from)) return;

            for (String imported : ourImports(file)) {
                String to = moduleOf(imported);
                if (!MODULES.contains(to) || to.equals(from) || to.equals("shared")) continue;
                found.computeIfAbsent(from + " -> " + to, key -> new TreeSet<>()).add(simpleName(file));
            }
        });
        return found;
    }

    /** The first path segment under the root package is the module. */
    private static String moduleOfFile(Path file) {
        List<String> parts = new ArrayList<>();
        ROOT.relativize(file).forEach(part -> parts.add(part.toString()));
        return parts.isEmpty() ? "" : parts.get(0);
    }

    private static String moduleOf(String importLine) {
        String fullyQualified = importLine.substring("import ".length()).replace(";", "").trim();
        if (fullyQualified.startsWith("static ")) fullyQualified = fullyQualified.substring(7);
        String rest = fullyQualified.substring(BASE.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    private static List<String> ourImports(Path file) {
        List<String> found = new ArrayList<>();
        Matcher matcher = OUR_IMPORT.matcher(read(file));
        while (matcher.find()) found.add("import " + matcher.group(1) + ";");
        return found;
    }

    private static String simpleName(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".java".length());
    }

    private static String relative(Path file) {
        return ROOT.relativize(file).toString();
    }

    private static Stream<Path> javaFilesUnder(Path directory) {
        try {
            return Files.walk(directory)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
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
