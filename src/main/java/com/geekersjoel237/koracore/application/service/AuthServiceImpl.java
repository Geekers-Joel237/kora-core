package com.geekersjoel237.koracore.application.service;

import com.geekersjoel237.koracore.application.port.in.AuthService;
import com.geekersjoel237.koracore.domain.exception.CustomerNotFoundException;
import com.geekersjoel237.koracore.domain.exception.InvalidOtpException;
import com.geekersjoel237.koracore.domain.exception.OtpExpiredException;
import com.geekersjoel237.koracore.domain.exception.PinValidationException;
import com.geekersjoel237.koracore.domain.model.Customer;
import com.geekersjoel237.koracore.domain.model.User;
import com.geekersjoel237.koracore.domain.port.CustomerPinEncoder;
import com.geekersjoel237.koracore.domain.port.CustomerRepository;
import com.geekersjoel237.koracore.domain.port.OtpStore;
import com.geekersjoel237.koracore.domain.port.UserRepository;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.domain.vo.Otp;
import com.geekersjoel237.koracore.domain.vo.TokenValue;
import com.geekersjoel237.koracore.domain.vo.Tokens;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class AuthServiceImpl implements AuthService {

    public static final int ACCESS_TOKEN_LIFETIME_MINUTES = 15;
    public static final int REFRESH_TOKEN_LIFETIME_DAYS = 7;
    private static final String DEFAULT_TEST_SECRET =
            "kora-core-test-secret-key-must-be-at-least-32-chars!";
    public static final int OTP_LIFETIME_MINUTES = 5;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final OtpStore otpStore;
    private final CustomerPinEncoder pinEncoder;
    private final Clock clock;
    private final String jwtSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthServiceImpl(UserRepository userRepository,
                           CustomerRepository customerRepository,
                           OtpStore otpStore,
                           CustomerPinEncoder pinEncoder,
                           Clock clock,
                           @Value("${jwt.secret:" + DEFAULT_TEST_SECRET + "}") String jwtSecret) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.otpStore = otpStore;
        this.pinEncoder = pinEncoder;
        this.clock = clock;
        this.jwtSecret = jwtSecret;
    }


    public AuthServiceImpl(UserRepository userRepository,
                           CustomerRepository customerRepository,
                           OtpStore otpStore,
                           CustomerPinEncoder pinEncoder,
                           Clock clock) {
        this(userRepository, customerRepository, otpStore, pinEncoder, clock, DEFAULT_TEST_SECRET);
    }


    @Override
    public void validatePin(Id customerId, String rawPin) {
        if (rawPin == null)
            throw new IllegalArgumentException("Pin cannot be null");

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Customer not found: " + customerId.value()));

        if (!customer.matchesPin(rawPin, pinEncoder))
            throw new PinValidationException("Invalid PIN");
    }


    @Override
    public String generateOtp(String email) {
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        Otp otp = Otp.of(code, Duration.ofMinutes(OTP_LIFETIME_MINUTES), clock);
        otpStore.save("otp:" + email, otp);
        return otp.code();
    }

    @Override
    public void verifyOtp(String email, String code) {
        String key = "otp:" + email;
        Otp otp = otpStore.get(key)
                .orElseThrow(() -> new OtpExpiredException(
                        "OTP not found, expired or already consumed for: " + email));

        if (!otp.matches(code))
            throw new InvalidOtpException("OTP code does not match");

        otpStore.delete(key);
    }


    @Override
    public Tokens generateTokens(User user) {
        Instant now = Instant.now(clock);
        Instant accessExpiry = now.plus(Duration.ofMinutes(ACCESS_TOKEN_LIFETIME_MINUTES));
        Instant refreshExpiry = now.plus(Duration.ofDays(REFRESH_TOKEN_LIFETIME_DAYS));

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        String accessValue = Jwts.builder()
                .subject(user.snapshot().id().value())
                .id(Id.generate().value())
                .claim("email", user.snapshot().email())
                .issuedAt(Date.from(now))
                .expiration(Date.from(accessExpiry))
                .signWith(key)
                .compact();

        String refreshValue = Jwts.builder()
                .subject(user.snapshot().id().value())
                .id(Id.generate().value())
                .issuedAt(Date.from(now))
                .expiration(Date.from(refreshExpiry))
                .signWith(key)
                .compact();

        return new Tokens(
                new TokenValue(accessValue, accessExpiry),
                new TokenValue(refreshValue, refreshExpiry)
        );
    }
}