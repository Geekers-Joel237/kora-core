package com.geekersjoel237.koracore.auth.adapters.in.rest.api.logout;

import com.geekersjoel237.koracore.auth.adapters.in.rest.mapping.AuthRequestMapper;
import com.geekersjoel237.koracore.shared.ports.in.CommandBus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogoutAction implements LogoutApi {

    private final CommandBus bus;
    private final AuthRequestMapper mapper;

    public LogoutAction(CommandBus bus, AuthRequestMapper mapper) {
        this.bus = bus;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<Void> logout(LogoutRequest request, String correlationId) {
        bus.dispatch(mapper.toCommand(request, correlationId));
        return ResponseEntity.noContent().build();
    }
}
