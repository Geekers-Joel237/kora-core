package com.geekersjoel237.koracore.web.api.test;

import com.geekersjoel237.koracore.domain.port.OtpStore;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Test-support endpoints — only active with the "perf" profile.
 * Permits k6 setup scripts to retrieve OTP codes without needing an SMTP server.
 * Security: permitted in SecurityConfig under /test/** — read-only, non-destructive.
 */
@Profile("perf")
@RestController
@RequestMapping("/test")
public class TestSupportAction {

    private final OtpStore otpStore;

    public TestSupportAction(OtpStore otpStore) {
        this.otpStore = otpStore;
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
}