package com.geekersjoel237.koracore.auth.adapters.in.rest.api.refreshToken;

import com.geekersjoel237.koracore.auth.domain.vo.Tokens;
import com.geekersjoel237.koracore.auth.adapters.in.rest.api.shared.TokensResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import com.geekersjoel237.koracore.shared.ports.in.CommandBus;
import com.geekersjoel237.koracore.shared.adapters.in.rest.CorrelationId;

@RestController
public class RefreshAction implements RefreshApi {

    private final CommandBus bus;

    public RefreshAction(CommandBus bus) {
        this.bus = bus;
    }

    @Override
    public ResponseEntity<TokensResponse> refresh(RefreshRequest request, String correlationId) {
        Tokens tokens = bus.dispatch(request.toCommand(CorrelationId.fromHeaderOrNew(correlationId)));
        return ResponseEntity.ok(TokensResponse.from(tokens));
    }
}