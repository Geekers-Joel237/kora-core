package com.geekersjoel237.koracore.payment.adapters.in.rest.api.reverse;

import com.geekersjoel237.koracore.payment.adapters.in.rest.api.shared.TransactionResponse;
import com.geekersjoel237.koracore.payment.adapters.in.rest.mapping.PaymentRequestMapper;
import com.geekersjoel237.koracore.payment.application.command.reversePayment.ReversePaymentResult;
import com.geekersjoel237.koracore.shared.ports.in.CommandBus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReverseAction implements ReverseApi {

    private final CommandBus bus;
    private final PaymentRequestMapper mapper;

    public ReverseAction(CommandBus bus, PaymentRequestMapper mapper) {
        this.bus = bus;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<TransactionResponse> reverse(String txId, ReverseRequest request, String correlationId) {
        ReversePaymentResult result = bus.dispatch(mapper.toCommand(request, txId, correlationId));
        return ResponseEntity.ok(TransactionResponse.from(result));
    }
}
