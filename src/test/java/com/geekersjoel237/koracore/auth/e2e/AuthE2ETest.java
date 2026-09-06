package com.geekersjoel237.koracore.auth.e2e;

import com.geekersjoel237.koracore.shared.e2e.AbstractE2ETest;
import com.geekersjoel237.koracore.auth.adapters.in.rest.api.shared.OtpResponse;
import com.geekersjoel237.koracore.auth.adapters.in.rest.api.shared.TokensResponse;
import com.geekersjoel237.koracore.auth.adapters.in.rest.api.register.RegisterRequest;
import com.geekersjoel237.koracore.auth.adapters.in.rest.api.verifyOtp.VerifyOtpRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class AuthE2ETest extends AbstractE2ETest {

    private static final String EMAIL    = "alice@example.com";
    private static final String FULL_NAME = "Alice";
    private static final String PREFIX   = "+225";
    private static final String PHONE    = "07000001001";
    private static final String PIN      = "1234";

    @Test
    void should_register_successfully_and_return_otp() {
        ResponseEntity<OtpResponse> response = register(FULL_NAME, EMAIL, PREFIX, PHONE, PIN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).containsIgnoringCase("otp");
    }

    @Test
    void should_return_409_when_email_already_registered() {
        register(FULL_NAME, EMAIL, PREFIX, PHONE, PIN);

        ResponseEntity<String> duplicate = http.postForEntity(url("/auth/register"),
                new RegisterRequest(
                        FULL_NAME, EMAIL, "+237", "677000001", PIN),
                String.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void should_verify_otp_and_return_access_and_refresh_tokens() {
        register(FULL_NAME, EMAIL, PREFIX, PHONE, PIN);
        String otp = waitAndGetOtpCode(EMAIL);

        ResponseEntity<TokensResponse> response = verifyOtp(EMAIL, otp);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokensResponse tokens = response.getBody();
        assertThat(tokens).isNotNull();
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.accessTokenExpiry()).isNotNull();
        assertThat(tokens.refreshTokenExpiry()).isNotNull();
    }

    @Test
    void should_return_401_for_invalid_otp_code() {
        register(FULL_NAME, EMAIL, PREFIX, PHONE, PIN);

        ResponseEntity<TokensResponse> response = verifyOtp(EMAIL, "000000");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void should_return_401_for_otp_with_unknown_email() {
        ResponseEntity<TokensResponse> response = verifyOtp("unknown@example.com", "123456");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void should_login_with_valid_pin_and_return_otp() {
        // First register + verify OTP to activate the account
        register(FULL_NAME, EMAIL, PREFIX, PHONE, PIN);
        String otp = waitAndGetOtpCode(EMAIL);
        verifyOtp(EMAIL, otp);

        ResponseEntity<OtpResponse> loginResp = login(EMAIL, PIN);

        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertNotNull(loginResp.getBody());
        assertThat(loginResp.getBody().message()).containsIgnoringCase("otp");
    }

    @Test
    void should_return_401_for_wrong_pin_on_login() {
        register(FULL_NAME, EMAIL, PREFIX, PHONE, PIN);
        String otp = waitAndGetOtpCode(EMAIL);
        verifyOtp(EMAIL, otp);

        ResponseEntity<OtpResponse> response = login(EMAIL, "0000");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void should_refresh_token_successfully() {
        TokensResponse tokens = registerAndLogin(EMAIL, FULL_NAME, PREFIX, PHONE, PIN);

        ResponseEntity<TokensResponse> response = refresh(tokens.refreshToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokensResponse newTokens = response.getBody();
        Assertions.assertNotNull(newTokens);
        assertThat(newTokens.accessToken()).isNotBlank();
        assertThat(newTokens.refreshToken()).isNotBlank();
    }

    // ── Validation tests ──────────────────────────────────────────────────────

    @Test
    void should_return_400_when_email_is_invalid() {
        ResponseEntity<String> response = http.postForEntity(
                url("/auth/register"),
                new RegisterRequest("Joel", "not-an-email", "+225", "07000001001", "1234"),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("email");
    }

    @Test
    void should_return_400_when_otp_code_is_not_six_digits() {
        ResponseEntity<String> response = http.postForEntity(
                url("/auth/verify-otp"),
                new VerifyOtpRequest("valid@example.com", "12"),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("code");
    }

    @Test
    void should_return_400_when_from_date_is_invalid_format() {
        SetupData ctx = setupCustomerWithAccount(
                "hist@example.com", "Hist", "+225", "07000009999", "1234");
        ResponseEntity<String> response = getWithToken(
                "/payments/history?from=not-a-date",
                ctx.tokens().accessToken(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("from");
    }
}