package com.geekersjoel237.koracore.e2e;

import com.geekersjoel237.koracore.web.api.auth.OtpResponse;
import com.geekersjoel237.koracore.web.api.auth.TokensResponse;
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
                new com.geekersjoel237.koracore.web.api.auth.RegisterRequest(
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
        assertThat(newTokens.accessToken()).isNotBlank();
        assertThat(newTokens.refreshToken()).isNotBlank();
    }
}