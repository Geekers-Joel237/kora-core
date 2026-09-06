package com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashIn;

import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.shared.TransactionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import com.geekersjoel237.koracore.shared.ports.in.CommandBus;
import com.geekersjoel237.koracore.payment.application.result.PaymentResult;
import com.geekersjoel237.koracore.shared.adapters.in.rest.CorrelationId;

@RestController
public class CashInAction implements CashInApi {

    private final CommandBus bus;

    public CashInAction(CommandBus bus) {
        this.bus = bus;
    }

    @Override
    public ResponseEntity<TransactionResponse> cashIn(String customerId, CashInRequest request, String correlationId) {
        PaymentResult result = bus.dispatch(request.toCommand(new Id(customerId), CorrelationId.fromHeaderOrNew(correlationId)));
        return ResponseEntity.ok(TransactionResponse.from(result));
    }
}