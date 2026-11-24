package com.geekersjoel237.koracore.auth.unit.domain.exception;

import com.geekersjoel237.koracore.auth.domain.exception.CustomerNotFoundException;
import com.geekersjoel237.koracore.auth.domain.exception.DuplicateEmailException;
import com.geekersjoel237.koracore.auth.domain.exception.InvalidOtpException;
import com.geekersjoel237.koracore.auth.domain.exception.InvalidTokenException;
import com.geekersjoel237.koracore.auth.domain.exception.MailDeliveryException;
import com.geekersjoel237.koracore.auth.domain.exception.OtpExpiredException;
import com.geekersjoel237.koracore.auth.domain.exception.PinValidationException;
import com.geekersjoel237.koracore.auth.domain.exception.UserNotActiveException;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRefusalCodesTest {

    static Stream<Arguments> refusals() {
        return Stream.of(
                Arguments.of(new CustomerNotFoundException("no customer"), "CUSTOMER_NOT_FOUND"),
                Arguments.of(new DuplicateEmailException("taken"), "EMAIL_ALREADY_REGISTERED"),
                Arguments.of(new InvalidOtpException("wrong code"), "INVALID_OTP"),
                Arguments.of(new OtpExpiredException("expired"), "OTP_EXPIRED"),
                Arguments.of(new PinValidationException("wrong pin"), "INVALID_PIN"),
                Arguments.of(new InvalidTokenException(), "TOKEN_INVALID"),
                Arguments.of(new UserNotActiveException("suspended"), "USER_NOT_ACTIVE"),
                Arguments.of(new MailDeliveryException("smtp down", new IllegalStateException()), "MAIL_DELIVERY_FAILED"));
    }

    @ParameterizedTest
    @MethodSource("refusals")
    void every_auth_refusal_names_its_code_and_the_catalog_documents_it(BusinessException refusal, String code)
            throws IOException {
        assertThat(refusal.code()).isEqualTo(code);
        assertThat(Files.readString(Path.of("docs", "problems.md")))
                .contains("### " + code.toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
