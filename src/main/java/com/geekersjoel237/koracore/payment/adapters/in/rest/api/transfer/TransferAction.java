package com.geekersjoel237.koracore.payment.adapters.in.rest.api.transfer;

import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.shared.TransactionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import com.geekersjoel237.koracore.shared.ports.in.CommandBus;
import com.geekersjoel237.koracore.payment.application.result.PaymentResult;
import com.geekersjoel237.koracore.shared.adapters.in.rest.CorrelationId;

@RestController
public class TransferAction implements TransferApi {

    private final CommandBus bus;

    public TransferAction(CommandBus bus) {
        this.bus = bus;
    }

    @Override
    public ResponseEntity<TransactionResponse> transfer(String customerId, TransferRequest request,
                                                       String correlationId) {
        PaymentResult result = bus.dispatch(
                request.toCommand(new Id(customerId), CorrelationId.fromHeaderOrNew(correlationId)));
        return ResponseEntity.ok(TransactionResponse.from(result));
    }
}