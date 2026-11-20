package com.geekersjoel237.koracore.auth.adapters.in.rest.api.verifyOtp;

import com.geekersjoel237.koracore.auth.adapters.in.rest.api.shared.TokensResponse;
import com.geekersjoel237.koracore.auth.adapters.in.rest.mapping.AuthRequestMapper;
import com.geekersjoel237.koracore.auth.domain.vo.Tokens;
import com.geekersjoel237.koracore.shared.ports.in.CommandBus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VerifyOtpAction implements VerifyOtpApi {

    private final CommandBus bus;
    private final AuthRequestMapper mapper;

    public VerifyOtpAction(CommandBus bus, AuthRequestMapper mapper) {
        this.bus = bus;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<TokensResponse> verifyOtp(VerifyOtpRequest request, String correlationId) {
        Tokens tokens = bus.dispatch(mapper.toCommand(request, correlationId));
        return ResponseEntity.ok(TokensResponse.from(tokens));
    }
}
