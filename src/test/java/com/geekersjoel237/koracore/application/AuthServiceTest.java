package com.geekersjoel237.koracore.application;

import com.geekersjoel237.koracore.application.service.AuthServiceImpl;
import com.geekersjoel237.koracore.domain.enums.Role;
import com.geekersjoel237.koracore.domain.enums.UserStatus;
import com.geekersjoel237.koracore.domain.exception.CustomerNotFoundException;
import com.geekersjoel237.koracore.domain.exception.InvalidOtpException;
import com.geekersjoel237.koracore.domain.exception.OtpExpiredException;
import com.geekersjoel237.koracore.domain.exception.PinValidationException;
import com.geekersjoel237.koracore.domain.model.Customer;
import com.geekersjoel237.koracore.domain.model.User;
import com.geekersjoel237.koracore.domain.port.CustomerPinEncoder;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.domain.vo.PhoneNumber;
import com.geekersjoel237.koracore.domain.vo.Tokens;
import com.geekersjoel237.koracore.infrastructure.security.BCryptCustomerPinEncoder;
import com.geekersjoel237.koracore.shared.inmemory.InMemoryCustomerRepository;
import com.geekersjoel237.koracore.shared.inmemory.InMemoryOtpStore;
import com.geekersjoel237.koracore.shared.inmemory.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import static java.time.temporal.ChronoUnit.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private static final Id     USER_ID = new Id("user-001");
    private static final Id     CUST_ID = new Id("user-001");
    private static final String EMAIL   = "test@koracore.com";
    private static final String RAW_PIN = "123456";

    private final CustomerPinEncoder pinEncoder = new BCryptCustomerPinEncoder();

    private InMemoryUserRepository     userRepo;
    private InMemoryCustomerRepository customerRepo;
    private InMemoryOtpStore           otpStore;
    private AuthServiceImpl            authService;

    @BeforeEach
    void setUp() {
        userRepo     = new InMemoryUserRepository();
        customerRepo = new InMemoryCustomerRepository();
        otpStore     = new InMemoryOtpStore(Clock.systemUTC());
        authService  = new AuthServiceImpl(userRepo, customerRepo, otpStore, pinEncoder, Clock.systemUTC());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User buildVerifiedUser() {
        return User.create(USER_ID, "Joel Geekers", EMAIL, Role.CUSTOMER);
    }

    private Customer buildCustomerWithPin(String rawPin) {
        User user = User.create(USER_ID, "Joel Geekers", EMAIL, Role.CUSTOMER);
        PhoneNumber phone = PhoneNumber.of("+237", "699887766");
        return Customer.create(user, phone, rawPin, pinEncoder);
    }

    // ── Groupe 1 — validatePin ────────────────────────────────────────────────

    @Test
    void should_not_throw_when_pin_is_correct() {
        customerRepo.save(buildCustomerWithPin(RAW_PIN));
        assertThatNoException().isThrownBy(() -> authService.validatePin(CUST_ID, RAW_PIN));
    }

    @Test
    void should_throw_pin_validation_exception_when_pin_is_wrong() {
        customerRepo.save(buildCustomerWithPin(RAW_PIN));
        assertThatThrownBy(() -> authService.validatePin(CUST_ID, "wrong!"))
                .isInstanceOf(PinValidationException.class);
    }

    @Test
    void should_throw_illegal_argument_exception_when_pin_is_null() {
        assertThatThrownBy(() -> authService.validatePin(CUST_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_customer_not_found_exception_when_customer_is_unknown() {
        assertThatThrownBy(() -> authService.validatePin(new Id("ghost"), RAW_PIN))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    // ── Groupe 2 — generateOtp + verifyOtp ───────────────────────────────────

    @Test
    void should_generate_six_digit_numeric_otp() {
        String code = authService.generateOtp(EMAIL);
        assertNotNull(code);
        assertTrue(code.matches("\\d{6}"));
    }

    @Test
    void should_generate_different_codes_on_successive_calls() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            otpStore.reset();
            codes.add(authService.generateOtp(EMAIL));
        }
        assertTrue(codes.size() > 1);
    }

    @Test
    void should_not_throw_and_delete_otp_after_successful_verify() {
        String code = authService.generateOtp(EMAIL);
        assertThatNoException().isThrownBy(() -> authService.verifyOtp(EMAIL, code));
        assertTrue(otpStore.get("otp:" + EMAIL).isEmpty());
    }

    @Test
    void should_throw_invalid_otp_exception_when_code_is_wrong() {
        authService.generateOtp(EMAIL);
        assertThatThrownBy(() -> authService.verifyOtp(EMAIL, "000000"))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void should_throw_otp_expired_exception_when_key_is_absent() {
        assertThatThrownBy(() -> authService.verifyOtp(EMAIL, "123456"))
                .isInstanceOf(OtpExpiredException.class);
    }

    @Test
    void should_throw_otp_expired_exception_when_otp_is_expired() {
        // Clock fixée dans le passé pour que le VO soit expiré dès création
        // (OTP créé à now-10min, TTL=5min → expiré vu depuis l'horloge système)
        Clock pastClock = Clock.fixed(Instant.now().minus(10, MINUTES), ZoneOffset.UTC);
        InMemoryOtpStore expiredStore   = new InMemoryOtpStore(pastClock);
        AuthServiceImpl  expiredService = new AuthServiceImpl(
                userRepo, customerRepo, expiredStore, pinEncoder, pastClock
        );
        String code = expiredService.generateOtp(EMAIL);
        assertThatThrownBy(() -> expiredService.verifyOtp(EMAIL, code))
                .isInstanceOf(OtpExpiredException.class);
    }

    @Test
    void should_throw_otp_expired_exception_when_otp_already_consumed() {
        String code = authService.generateOtp(EMAIL);
        authService.verifyOtp(EMAIL, code);
        assertThatThrownBy(() -> authService.verifyOtp(EMAIL, code))
                .isInstanceOf(OtpExpiredException.class);
    }

    // ── Groupe 3 — generateTokens ─────────────────────────────────────────────

    @Test
    void should_generate_access_token_with_expiry_in_expected_window() {
        Instant before = Instant.now();
        Tokens tokens  = authService.generateTokens(buildVerifiedUser());
        Instant after  = Instant.now();

        assertThat(tokens.accessToken().expiredAt())
                .isAfter(before.plus(14, MINUTES))
                .isBefore(after.plus(16, MINUTES));
    }

    @Test
    void should_generate_refresh_token_with_expiry_in_expected_window() {
        Instant before = Instant.now();
        Tokens tokens  = authService.generateTokens(buildVerifiedUser());
        Instant after  = Instant.now();

        assertThat(tokens.refreshToken().expiredAt())
                .isAfter(before.plus(6, DAYS).plus(23, HOURS))
                .isBefore(after.plus(7, DAYS).plus(1, HOURS));
    }

    @Test
    void should_generate_distinct_token_values_on_successive_calls() {
        Tokens t1 = authService.generateTokens(buildVerifiedUser());
        Tokens t2 = authService.generateTokens(buildVerifiedUser());
        assertNotEquals(t1.accessToken().value(),  t2.accessToken().value());
        assertNotEquals(t1.refreshToken().value(), t2.refreshToken().value());
    }

    @Test
    void should_return_non_blank_access_token_value() {
        Tokens tokens = authService.generateTokens(buildVerifiedUser());
        assertFalse(tokens.accessToken().value().isBlank());
    }

    @Test
    void should_return_non_blank_refresh_token_value() {
        Tokens tokens = authService.generateTokens(buildVerifiedUser());
        assertFalse(tokens.refreshToken().value().isBlank());
    }
}