package com.geekersjoel237.koracore.web.api.auth.refreshToken;

import com.geekersjoel237.koracore.application.port.in.AuthService;
import com.geekersjoel237.koracore.domain.vo.Tokens;
import com.geekersjoel237.koracore.web.api.auth.shared.TokensResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RefreshAction implements RefreshApi {

    private final AuthService authService;

    public RefreshAction(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public ResponseEntity<TokensResponse> refresh(RefreshRequest request) {
        Tokens tokens = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(TokensResponse.from(tokens));
    }
}