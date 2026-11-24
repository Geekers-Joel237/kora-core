package com.geekersjoel237.koracore.shared.unit.domain.exception;

import com.geekersjoel237.koracore.shared.application.cqrs.CommandReplayedException;
import com.geekersjoel237.koracore.shared.application.transaction.RetryExhaustedException;
import com.geekersjoel237.koracore.shared.domain.exception.CurrencyMismatchException;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SharedRefusalCodesTest {

    private static final String[] FRAMEWORK_CODES = {
            "VALIDATION_FAILED", "MALFORMED_REQUEST", "INVALID_ARGUMENT", "UNAUTHENTICATED", "FORBIDDEN"};

    static Stream<Arguments> refusals() {
        Supplier<String> currencyMismatch = () -> new CurrencyMismatchException("XAF", "EUR").code();
        Supplier<String> replayed = () -> new CommandReplayedException(Id.generate()).code();
        Supplier<String> exhausted = () -> new RetryExhaustedException("five races lost", new IllegalStateException()).code();
        return Stream.of(
                Arguments.of(currencyMismatch, "CURRENCY_MISMATCH"),
                Arguments.of(replayed, "COMMAND_REPLAYED"),
                Arguments.of(exhausted, "RETRY_EXHAUSTED"));
    }

    static Stream<String> frameworkCodes() {
        return Stream.of(FRAMEWORK_CODES);
    }

    @ParameterizedTest
    @MethodSource("refusals")
    void every_kernel_refusal_names_its_code_and_the_catalog_documents_it(Supplier<String> refusal, String code)
            throws IOException {
        assertThat(refusal.get()).isEqualTo(code);
        assertThat(catalog()).contains(anchorOf(code));
    }

    @ParameterizedTest
    @MethodSource("frameworkCodes")
    void the_catalog_documents_the_codes_the_web_edge_gives_to_framework_failures(String code) throws IOException {
        assertThat(catalog()).contains(anchorOf(code));
    }

    private static String catalog() throws IOException {
        return Files.readString(Path.of("docs", "problems.md"));
    }

    private static String anchorOf(String code) {
        return "### " + code.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
