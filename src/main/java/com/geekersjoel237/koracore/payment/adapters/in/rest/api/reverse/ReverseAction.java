package com.geekersjoel237.koracore.payment.adapters.in.rest.api.reverse;

import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.shared.TransactionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import com.geekersjoel237.koracore.shared.ports.in.CommandBus;
import com.geekersjoel237.koracore.payment.application.result.PaymentResult;

@RestController
public class ReverseAction implements ReverseApi {

    private final CommandBus bus;

    public ReverseAction(CommandBus bus) {
        this.bus = bus;
    }

    @Override
    public ResponseEntity<TransactionResponse> reverse(String txId, ReverseRequest request, String correlationId) {
        PaymentResult result = bus.dispatch(request.toCommand(new Id(txId)));
        return ResponseEntity.ok(TransactionResponse.from(result));
    }
}