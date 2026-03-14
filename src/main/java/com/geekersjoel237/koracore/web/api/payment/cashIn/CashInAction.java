package com.geekersjoel237.koracore.web.api.payment.cashIn;

import com.geekersjoel237.koracore.application.port.in.PaymentUseCase;
import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.web.api.payment.shared.TransactionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CashInAction implements CashInApi {

    private final PaymentUseCase paymentUseCase;

    public CashInAction(PaymentUseCase paymentUseCase) {
        this.paymentUseCase = paymentUseCase;
    }

    @Override
    public ResponseEntity<TransactionResponse> cashIn(String customerId, CashInRequest request) {
        Transaction tx = paymentUseCase.cashIn(request.toCommand(new Id(customerId)));
        return ResponseEntity.ok(TransactionResponse.from(tx));
    }
}