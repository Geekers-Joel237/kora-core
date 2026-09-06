package com.geekersjoel237.koracore.auth.unit.application;

import com.geekersjoel237.koracore.auth.domain.enums.OtpPurpose;
import com.geekersjoel237.koracore.shared.ports.out.mail.Mail;
import com.geekersjoel237.koracore.auth.ports.out.otp.OtpChallenge;
import com.geekersjoel237.koracore.auth.ports.out.security.TokenIssuer;
import com.geekersjoel237.koracore.auth.adapters.out.otp.MailedOtpChallenge;
import com.geekersjoel237.koracore.auth.adapters.out.security.JwtTokenIssuer;
import com.geekersjoel237.koracore.auth.domain.enums.Role;
import com.geekersjoel237.koracore.auth.domain.exception.CustomerNotFoundException;
import com.geekersjoel237.koracore.auth.domain.exception.InvalidOtpException;
import com.geekersjoel237.koracore.auth.domain.exception.OtpExpiredException;
import com.geekersjoel237.koracore.auth.domain.exception.PinValidationException;
import com.geekersjoel237.koracore.auth.domain.model.Customer;
import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.ports.out.security.CustomerPinEncoder;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.auth.domain.vo.PhoneNumber;
import com.geekersjoel237.koracore.auth.domain.vo.Tokens;
import com.geekersjoel237.koracore.auth.config.SecurityProperties;
import com.geekersjoel237.koracore.auth.adapters.out.security.BCryptCustomerPinEncoder;
import com.geekersjoel237.koracore.payment.unit.doubles.InMemoryAccountRepository;
import com.geekersjoel237.koracore.auth.unit.doubles.InMemoryCustomerRepository;
import com.geekersjoel237.koracore.shared.unit.doubles.InMemoryMailPort;
import com.geekersjoel237.koracore.shared.adapters.out.store.InMemoryExpiringStore;
import com.geekersjoel237.koracore.shared.unit.doubles.MutableClock;
import com.geekersjoel237.koracore.shared.ports.out.store.ExpiringStore;
import com.geekersjoel237.koracore.auth.unit.doubles.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;
import com.geekersjoel237.koracore.auth.adapters.out.security.CustomerPinVerifier;
import com.geekersjoel237.koracore.auth.domain.vo.OtpCode;
import java.time.Duration;

import static java.time.temporal.ChronoUnit.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private static final Id USER_ID = new Id("user-001");
    private static final Id CUST_ID = new Id("user-001");
    private static final String EMAIL = "test@koracore.com";
    private static final Pin RAW_PIN = Pin.of("123456");

    private static final SecurityProperties TEST_SECURITY = new SecurityProperties(
            new SecurityProperties.Jwt("test-secret-key-must-be-at-least-32-chars!!", 15, 7),
            new SecurityProperties.Otp(5)
    );

    /** The value the challenge actually needs — no configuration record in sight. */
    private static final Duration OTP_VALIDITY = Duration.ofMinutes(5);

    private final CustomerPinEncoder pinEncoder = new BCryptCustomerPinEncoder();
    private InMemoryUserRepository userRepo;
    private InMemoryCustomerRepository customerRepo;
    private InMemoryAccountRepository accountRepo;
    private MutableClock clock;
    private ExpiringStore<OtpCode> otpCodes;
    private InMemoryMailPort mailPort;
    private OtpChallenge otpChallenge;
    private TokenIssuer tokenIssuer;
    private CustomerPinVerifier pinVerifier;

    @BeforeEach
    void setUp() {
        userRepo = new InMemoryUserRepository();
        customerRepo = new InMemoryCustomerRepository();
        accountRepo = new InMemoryAccountRepository();
        clock = MutableClock.at(Instant.now());
        otpCodes = new InMemoryExpiringStore<>(clock);
        mailPort = new InMemoryMailPort();
        pinVerifier = new CustomerPinVerifier(customerRepo, pinEncoder);
        otpChallenge = new MailedOtpChallenge(otpCodes, mailPort, OTP_VALIDITY);
        tokenIssuer = new JwtTokenIssuer(TEST_SECURITY, Clock.systemUTC());
    }

    private static final OtpPurpose PURPOSE = OtpPurpose.LOGIN;
    private static final Pattern SIX_DIGITS = Pattern.compile("\\b(\\d{6})\\b");

    /**
     * The challenge no longer hands the code back. It is read out of the mail, as its
     * recipient would — not out of the store, which hides an entry once it expires.
     */
    private String issueAndReadCode() {
        otpChallenge.issue(EMAIL, PURPOSE);
        return codeInTheMailTo(EMAIL);
    }

    /**
     * Digs the code out of the message body, which is the only place a recipient could
     * find it. Reading it from anywhere else would let a body that never contained it
     * pass — which is exactly what happened while the adapter dropped the argument.
     */
    private String codeInTheMailTo(String email) {
        Mail mail = mailPort.lastMailTo(email).orElseThrow(
                () -> new AssertionError("No mail was sent to " + email));
        Matcher matcher = SIX_DIGITS.matcher(mail.body());
        if (!matcher.find())
            throw new AssertionError("No six-digit code in the mail body: " + mail.body());
        return matcher.group(1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User buildVerifiedUser() {
        return User.create(USER_ID, "Joel Geekers", EMAIL, Role.CUSTOMER);
    }

    private Customer buildCustomerWithPin(Pin pin) {
        User user = User.create(USER_ID, "Joel Geekers", EMAIL, Role.CUSTOMER);
        PhoneNumber phone = PhoneNumber.of("+237", "699887766");
        return Customer.create(user, phone, pin, pinEncoder);
    }

    // ── Groupe 1 — validatePin ────────────────────────────────────────────────

    @Test
    void should_not_throw_when_pin_is_correct() {
        customerRepo.save(buildCustomerWithPin(RAW_PIN));
        assertThatNoException().isThrownBy(() -> pinVerifier.verify(CUST_ID, RAW_PIN));
    }

    @Test
    void should_throw_pin_validation_exception_when_pin_is_wrong() {
        customerRepo.save(buildCustomerWithPin(RAW_PIN));
        assertThatThrownBy(() -> pinVerifier.verify(CUST_ID, Pin.of("wrong!")))
                .isInstanceOf(PinValidationException.class);
    }

    @Test
    void should_throw_illegal_argument_exception_when_pin_is_null() {
        assertThatThrownBy(() -> pinVerifier.verify(CUST_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_customer_not_found_exception_when_customer_is_unknown() {
        assertThatThrownBy(() -> pinVerifier.verify(new Id("ghost"), RAW_PIN))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    // ── Groupe 2 — generateOtp + verifyOtp ───────────────────────────────────

    @Test
    void should_generate_six_digit_numeric_otp() {
        String code = issueAndReadCode();
        assertNotNull(code);
        assertTrue(code.matches("\\d{6}"));
    }

    @Test
    void should_generate_different_codes_on_successive_calls() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            codes.add(issueAndReadCode());
        }
        assertTrue(codes.size() > 1);
    }

    @Test
    void should_not_throw_and_delete_otp_after_successful_verify() {
        String code = issueAndReadCode();
        assertThatNoException().isThrownBy(() -> otpChallenge.consume(EMAIL, OtpCode.of(code)));
        assertTrue(otpCodes.get("otp:" + EMAIL).isEmpty());
    }

    @Test
    void should_throw_invalid_otp_exception_when_code_is_wrong() {
        otpChallenge.issue(EMAIL, PURPOSE);
        assertThatThrownBy(() -> otpChallenge.consume(EMAIL, OtpCode.of("000000")))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void should_throw_otp_expired_exception_when_key_is_absent() {
        assertThatThrownBy(() -> otpChallenge.consume(EMAIL, OtpCode.of("123456")))
                .isInstanceOf(OtpExpiredException.class);
    }

    @Test
    void should_throw_otp_expired_exception_when_otp_is_expired() {
        // One store, one challenge, and time moving past the window. The store owns
        // the lifetime now, so nothing here has to hold a second opinion about it.
        otpChallenge.issue(EMAIL, PURPOSE);
        String code = codeInTheMailTo(EMAIL);

        clock.advance(OTP_VALIDITY.plusSeconds(1));

        assertThatThrownBy(() -> otpChallenge.consume(EMAIL, OtpCode.of(code)))
                .isInstanceOf(OtpExpiredException.class);
    }

    @Test
    void should_throw_otp_expired_exception_when_otp_already_consumed() {
        String code = issueAndReadCode();
        otpChallenge.consume(EMAIL, OtpCode.of(code));
        assertThatThrownBy(() -> otpChallenge.consume(EMAIL, OtpCode.of(code)))
                .isInstanceOf(OtpExpiredException.class);
    }

    // ── Groupe 3 — generateTokens ─────────────────────────────────────────────

    @Test
    void should_generate_access_token_with_expiry_in_expected_window() {
        Instant before = Instant.now();
        Tokens tokens = tokenIssuer.issue(buildVerifiedUser());
        Instant after = Instant.now();

        assertThat(tokens.accessToken().expiredAt())
                .isAfter(before.plus(14, MINUTES))
                .isBefore(after.plus(16, MINUTES));
    }

    @Test
    void should_generate_refresh_token_with_expiry_in_expected_window() {
        Instant before = Instant.now();
        Tokens tokens = tokenIssuer.issue(buildVerifiedUser());
        Instant after = Instant.now();

        assertThat(tokens.refreshToken().expiredAt())
                .isAfter(before.plus(6, DAYS).plus(23, HOURS))
                .isBefore(after.plus(7, DAYS).plus(1, HOURS));
    }

    @Test
    void should_generate_distinct_token_values_on_successive_calls() {
        Tokens t1 = tokenIssuer.issue(buildVerifiedUser());
        Tokens t2 = tokenIssuer.issue(buildVerifiedUser());
        assertNotEquals(t1.accessToken().value(), t2.accessToken().value());
        assertNotEquals(t1.refreshToken().value(), t2.refreshToken().value());
    }

    @Test
    void should_return_non_blank_access_token_value() {
        Tokens tokens = tokenIssuer.issue(buildVerifiedUser());
        assertFalse(tokens.accessToken().value().isBlank());
    }

    @Test
    void should_return_non_blank_refresh_token_value() {
        Tokens tokens = tokenIssuer.issue(buildVerifiedUser());
        assertFalse(tokens.refreshToken().value().isBlank());
    }
}