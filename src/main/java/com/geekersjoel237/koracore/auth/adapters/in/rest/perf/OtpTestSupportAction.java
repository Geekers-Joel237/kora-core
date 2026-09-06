package com.geekersjoel237.koracore.auth.adapters.in.rest.perf;

import com.geekersjoel237.koracore.auth.domain.vo.OtpCode;
import com.geekersjoel237.koracore.shared.ports.out.store.ExpiringStore;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@Profile("perf")
@RestController
@RequestMapping("/test")
public class OtpTestSupportAction {

    private final ExpiringStore<OtpCode> otpCodes;

    public OtpTestSupportAction(ExpiringStore<OtpCode> otpCodes) {
        this.otpCodes = otpCodes;
    }

    /** 404 when no OTP exists for the address, or it has expired. */
    @GetMapping("/otp/{email}")
    public ResponseEntity<Map<String, String>> getOtp(@PathVariable String email) {
        return otpCodes.get("otp:" + email)
                .map(code -> ResponseEntity.ok(Map.of("code", code.value())))
                .orElse(ResponseEntity.notFound().build());
    }
}
