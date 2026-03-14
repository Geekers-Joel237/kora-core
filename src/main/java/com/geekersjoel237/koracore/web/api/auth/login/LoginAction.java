package com.geekersjoel237.koracore.web.api.auth.login;

import com.geekersjoel237.koracore.application.port.in.AuthUseCase;
import com.geekersjoel237.koracore.web.api.auth.shared.OtpResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginAction implements LoginApi {

    private final AuthUseCase authUsecase;

    public LoginAction(AuthUseCase authUsecase) {
        this.authUsecase = authUsecase;
    }

    @Override
    public ResponseEntity<OtpResponse> login(LoginRequest request) {
        authUsecase.login(request.toCommand());
        return ResponseEntity.ok(new OtpResponse("OTP sent to your email"));
    }
}