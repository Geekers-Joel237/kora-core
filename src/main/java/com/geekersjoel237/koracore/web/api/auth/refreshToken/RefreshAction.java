package com.geekersjoel237.koracore.web.api.auth.refreshToken;

import com.geekersjoel237.koracore.application.port.in.AuthUseCase;
import com.geekersjoel237.koracore.domain.vo.Tokens;
import com.geekersjoel237.koracore.web.api.auth.shared.TokensResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RefreshAction implements RefreshApi {

    private final AuthUseCase authUsecase;

    public RefreshAction(AuthUseCase authUsecase) {
        this.authUsecase = authUsecase;
    }

    @Override
    public ResponseEntity<TokensResponse> refresh(RefreshRequest request) {
        Tokens tokens = authUsecase.refresh(request.refreshToken());
        return ResponseEntity.ok(TokensResponse.from(tokens));
    }
}