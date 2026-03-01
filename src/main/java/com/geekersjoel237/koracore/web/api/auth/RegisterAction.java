package com.geekersjoel237.koracore.web.api.auth;

import com.geekersjoel237.koracore.application.port.in.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RegisterAction implements RegisterApi {

    private final AuthService authService;

    public RegisterAction(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public ResponseEntity<OtpResponse> register(RegisterRequest request) {
        String otp = authService.register(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(new OtpResponse(otp));
    }
}