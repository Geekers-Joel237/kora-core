package com.geekersjoel237.koracore.web.api.auth;

import com.geekersjoel237.koracore.application.port.in.AuthService;
import com.geekersjoel237.koracore.domain.vo.Tokens;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VerifyOtpAction implements VerifyOtpApi {

    private final AuthService authService;

    public VerifyOtpAction(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public ResponseEntity<TokensResponse> verifyOtp(VerifyOtpRequest request) {
        Tokens tokens = authService.verifyOtpAndGetTokens(request.email(), request.code());
        return ResponseEntity.ok(TokensResponse.from(tokens));
    }
}