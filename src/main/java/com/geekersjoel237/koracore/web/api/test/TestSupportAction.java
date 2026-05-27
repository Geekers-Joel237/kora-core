package com.geekersjoel237.koracore.web.api.test;

import com.geekersjoel237.koracore.domain.SystemConstants;
import com.geekersjoel237.koracore.domain.model.Account;
import com.geekersjoel237.koracore.domain.port.AccountRepository;
import com.geekersjoel237.koracore.domain.port.OtpStore;
import com.geekersjoel237.koracore.domain.vo.Id;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Test-support endpoints — only active with the "perf" profile.
 * Permits k6 setup scripts to retrieve OTP codes without needing an SMTP server.
 * Security: permitted in SecurityConfig under /test/** — non-destructive in prod (profile guard).
 */
@Profile("perf")
@RestController
@RequestMapping("/test")
public class TestSupportAction {

    private final OtpStore otpStore;
    private final JdbcTemplate jdbcTemplate;
    private final AccountRepository accountRepository;

    public TestSupportAction(OtpStore otpStore,
                             JdbcTemplate jdbcTemplate,
                             AccountRepository accountRepository) {
        this.otpStore          = otpStore;
        this.jdbcTemplate      = jdbcTemplate;
        this.accountRepository = accountRepository;
    }

    /**
     * Returns the current OTP code for an email address.
     * k6 setup uses this to complete the register → verify-otp flow.
     * Returns 404 if no OTP exists or it has expired.
     */
    @GetMapping("/otp/{email}")
    public ResponseEntity<Map<String, String>> getOtp(@PathVariable String email) {
        String key = "otp:" + email;
        return otpStore.get(key)
                .map(otp -> ResponseEntity.ok(Map.of("code", otp.code())))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Truncates all test data and recreates the float account.
     * Called by load-run.sh before each k6 run to guarantee a clean initial state.
     * The ledger row is preserved — it is a singleton created at startup.
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE authorization_records, operations, trx_state_historics, " +
                "transactions, accounts, customers, users RESTART IDENTITY CASCADE"
        );
        accountRepository.save(Account.createFloatAccount(Id.generate(), SystemConstants.PROVIDER_ID));
        return ResponseEntity.ok(Map.of(
                "status",  "reset",
                "message", "All test data cleared. Float account recreated."
        ));
    }
}