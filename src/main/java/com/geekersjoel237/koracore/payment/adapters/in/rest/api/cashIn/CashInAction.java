package com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashIn;

import com.geekersjoel237.koracore.payment.adapters.in.rest.api.shared.TransactionResponse;
import com.geekersjoel237.koracore.payment.adapters.in.rest.mapping.PaymentRequestMapper;
import com.geekersjoel237.koracore.payment.application.result.PaymentResult;
import com.geekersjoel237.koracore.shared.ports.in.CommandBus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CashInAction implements CashInApi {

    private final CommandBus bus;
    private final PaymentRequestMapper mapper;

    public CashInAction(CommandBus bus, PaymentRequestMapper mapper) {
        this.bus = bus;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<TransactionResponse> cashIn(String customerId, CashInRequest request, String correlationId) {
        PaymentResult result = bus.dispatch(mapper.toCommand(request, customerId, correlationId));
        return ResponseEntity.ok(TransactionResponse.from(result));
    }
}
