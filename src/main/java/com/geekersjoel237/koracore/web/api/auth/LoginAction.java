package com.geekersjoel237.koracore.web.api.auth;

import com.geekersjoel237.koracore.application.port.in.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginAction implements LoginApi {

    private final AuthService authService;

    public LoginAction(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public ResponseEntity<OtpResponse> login(LoginRequest request) {
        authService.login(request.toCommand());
        return ResponseEntity.ok(new OtpResponse("OTP sent to your email"));
    }
}