package com.geekersjoel237.koracore.web.api.auth.register;

import com.geekersjoel237.koracore.application.port.in.AuthUseCase;
import com.geekersjoel237.koracore.web.api.auth.shared.OtpResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RegisterAction implements RegisterApi {

    private final AuthUseCase authUsecase;

    public RegisterAction(AuthUseCase authUsecase) {
        this.authUsecase = authUsecase;
    }

    @Override
    public ResponseEntity<OtpResponse> register(RegisterRequest request) {
        authUsecase.register(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(new OtpResponse("OTP sent to your email"));
    }
}