package com.geekersjoel237.koracore.auth.adapters.in.rest.api.logout;

import com.geekersjoel237.koracore.shared.adapters.in.rest.CorrelationId;
import com.geekersjoel237.koracore.shared.ports.in.CommandBus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogoutAction implements LogoutApi {

    private final CommandBus bus;

    public LogoutAction(CommandBus bus) {
        this.bus = bus;
    }

    @Override
    public ResponseEntity<Void> logout(LogoutRequest request, String correlationId) {
        bus.dispatch(request.toCommand(CorrelationId.fromHeaderOrNew(correlationId)));
        return ResponseEntity.noContent().build();
    }
}
