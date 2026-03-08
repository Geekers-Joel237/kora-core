package com.geekersjoel237.koracore.e2e;

import com.geekersjoel237.koracore.domain.model.Account;
import com.geekersjoel237.koracore.domain.model.Customer;
import com.geekersjoel237.koracore.domain.port.AccountRepository;
import com.geekersjoel237.koracore.domain.port.CustomerRepository;
import com.geekersjoel237.koracore.domain.port.OtpStore;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.domain.vo.Otp;
import com.geekersjoel237.koracore.web.api.auth.LoginRequest;
import com.geekersjoel237.koracore.web.api.auth.OtpResponse;
import com.geekersjoel237.koracore.web.api.auth.RefreshRequest;
import com.geekersjoel237.koracore.web.api.auth.RegisterRequest;
import com.geekersjoel237.koracore.web.api.auth.TokensResponse;
import com.geekersjoel237.koracore.web.api.auth.VerifyOtpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for all E2E tests.
 *
 * Starts a real HTTP server (RANDOM_PORT) backed by a shared PostgreSQL Testcontainer.
 * Each test gets a clean database via TRUNCATE in @BeforeEach — no @Transactional rollback
 * since requests go through HTTP.
 *
 * Uses RestTemplate with a lenient error handler (never throws on 4xx/5xx) so tests
 * can assert on error status codes without catching exceptions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestMailConfig.class)
public abstract class AbstractE2ETest {

    protected static final Id SYSTEM_PROVIDER_ID = new Id("provider-system-001");

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void overridePostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    protected AccountRepository accountRepository;

    @Autowired
    protected CustomerRepository customerRepository;

    @Autowired
    protected OtpStore otpStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Lenient RestTemplate — never throws on any HTTP status code. */
    protected RestTemplate http;

    @BeforeEach
    void setupRestTemplateAndTruncate() {
        http = new RestTemplate();
        http.setErrorHandler(new NoOpResponseErrorHandler());

        jdbcTemplate.execute(
                "TRUNCATE TABLE operations, trx_state_historics, transactions, accounts, customers, users");
        accountRepository.save(Account.createFloatAccount(Id.generate(), SYSTEM_PROVIDER_ID));
    }

    // ── URL builder ───────────────────────────────────────────────────────────

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    // ── Auth helpers ──────────────────────────────────────────────────────────

    protected ResponseEntity<OtpResponse> register(String fullName, String email,
                                                    String prefix, String phone, String pin) {
        return http.postForEntity(url("/auth/register"),
                new RegisterRequest(fullName, email, prefix, phone, pin),
                OtpResponse.class);
    }

    protected ResponseEntity<TokensResponse> verifyOtp(String email, String otp) {
        return http.postForEntity(url("/auth/verify-otp"),
                new VerifyOtpRequest(email, otp),
                TokensResponse.class);
    }

    protected ResponseEntity<OtpResponse> login(String email, String pin) {
        return http.postForEntity(url("/auth/login"),
                new LoginRequest(email, pin),
                OtpResponse.class);
    }

    protected ResponseEntity<TokensResponse> refresh(String refreshToken) {
        return http.postForEntity(url("/auth/refresh"),
                new RefreshRequest(refreshToken),
                TokensResponse.class);
    }

    /**
     * Convenience: register → verify OTP → return tokens.
     */
    protected TokensResponse registerAndLogin(String email, String fullName,
                                               String prefix, String phone, String pin) {
        register(fullName, email, prefix, phone, pin);
        String otp = waitAndGetOtpCode(email);
        return verifyOtp(email, otp).getBody();
    }

    /**
     * Registers a customer via API, creates their customer account in DB,
     * and returns tokens + their customerId. Used by payment E2E tests.
     */
    protected SetupData setupCustomerWithAccount(String email, String fullName,
                                                  String prefix, String phone, String pin) {
        TokensResponse tokens = registerAndLogin(email, fullName, prefix, phone, pin);
        Customer customer = customerRepository.findByEmail(email).orElseThrow();
        Id customerId = customer.snapshot().customerId();
        Id accountId = accountRepository.findByCustomerId(customerId)
                .map(a -> a.snapshot().accountId())
                .orElseGet(() -> {
                    Id newId = Id.generate();
                    accountRepository.save(Account.createCustomerAccount(newId, customerId));
                    return newId;
                });
        return new SetupData(tokens, customerId, accountId);
    }

    protected record SetupData(TokensResponse tokens, Id customerId, Id accountId) {}

    // ── OTP helper ─────────────────────────────────────────────────────────────

    protected String waitAndGetOtpCode(String email) {
        String key = "otp:" + email;
        for (int i = 0; i < 20; i++) { // up to ~1s total
            var otpOpt = otpStore.get(key);
            if (otpOpt.isPresent()) {
                return otpOpt.get().code();
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
        }
        throw new AssertionError("OTP not found in store for email: " + email);
    }

    // ── HTTP with Bearer token ─────────────────────────────────────────────────

    protected <T> ResponseEntity<T> postWithToken(String path, Object body,
                                                   String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(url(path), HttpMethod.POST,
                new HttpEntity<>(body, headers), responseType);
    }
}