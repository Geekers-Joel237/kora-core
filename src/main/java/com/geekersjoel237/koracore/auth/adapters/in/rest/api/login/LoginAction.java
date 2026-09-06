package com.geekersjoel237.koracore.auth.adapters.in.rest.api.login;

import com.geekersjoel237.koracore.auth.adapters.in.rest.api.shared.OtpResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import com.geekersjoel237.koracore.shared.ports.in.CommandBus;
import com.geekersjoel237.koracore.shared.adapters.in.rest.CorrelationId;

@RestController
public class LoginAction implements LoginApi {

    private final CommandBus bus;

    public LoginAction(CommandBus bus) {
        this.bus = bus;
    }

    @Override
    public ResponseEntity<OtpResponse> login(LoginRequest request, String correlationId) {
        bus.dispatch(request.toCommand(CorrelationId.fromHeaderOrNew(correlationId)));
        return ResponseEntity.ok(new OtpResponse("OTP sent to your email"));
    }
}