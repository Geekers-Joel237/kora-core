package com.geekersjoel237.koracore.payment.adapters.in.rest.api.transfer;

import com.geekersjoel237.koracore.payment.adapters.in.rest.api.shared.TransactionResponse;
import com.geekersjoel237.koracore.payment.adapters.in.rest.mapping.PaymentRequestMapper;
import com.geekersjoel237.koracore.payment.application.command.transfer.TransferResult;
import com.geekersjoel237.koracore.shared.ports.in.CommandBus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferAction implements TransferApi {

    private final CommandBus bus;
    private final PaymentRequestMapper mapper;

    public TransferAction(CommandBus bus, PaymentRequestMapper mapper) {
        this.bus = bus;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<TransactionResponse> transfer(String customerId, TransferRequest request,
                                                       String correlationId) {
        TransferResult result = bus.dispatch(mapper.toCommand(request, customerId, correlationId));
        return ResponseEntity.ok(TransactionResponse.from(result));
    }
}
