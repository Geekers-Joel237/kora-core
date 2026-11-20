package com.geekersjoel237.koracore.auth.adapters.in.rest.api.login;

import com.geekersjoel237.koracore.auth.adapters.in.rest.api.shared.OtpResponse;
import com.geekersjoel237.koracore.auth.adapters.in.rest.mapping.AuthRequestMapper;
import com.geekersjoel237.koracore.shared.ports.in.CommandBus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginAction implements LoginApi {

    private final CommandBus bus;
    private final AuthRequestMapper mapper;

    public LoginAction(CommandBus bus, AuthRequestMapper mapper) {
        this.bus = bus;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<OtpResponse> login(LoginRequest request, String correlationId) {
        bus.dispatch(mapper.toCommand(request, correlationId));
        return ResponseEntity.ok(new OtpResponse("OTP sent to your email"));
    }
}
