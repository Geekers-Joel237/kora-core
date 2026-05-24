package com.geekersjoel237.koracore.web.api.auth.verifyOtp;

import com.geekersjoel237.koracore.application.port.in.AuthUseCase;
import com.geekersjoel237.koracore.domain.vo.Tokens;
import com.geekersjoel237.koracore.web.api.auth.shared.TokensResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VerifyOtpAction implements VerifyOtpApi {

    private final AuthUseCase authUsecase;

    public VerifyOtpAction(AuthUseCase authUsecase) {
        this.authUsecase = authUsecase;
    }

    @Override
    public ResponseEntity<TokensResponse> verifyOtp(VerifyOtpRequest request) {
        Tokens tokens = authUsecase.verifyOtpAndGetTokens(request.email(), request.code());
        return ResponseEntity.ok(TokensResponse.from(tokens));
    }
}