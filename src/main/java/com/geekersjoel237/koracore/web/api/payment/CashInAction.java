package com.geekersjoel237.koracore.web.api.payment;

import com.geekersjoel237.koracore.application.port.in.PaymentService;
import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.vo.Id;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CashInAction implements CashInApi {

    private final PaymentService paymentService;

    public CashInAction(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public ResponseEntity<TransactionResponse> cashIn(String customerId, CashInRequest request) {
        Transaction tx = paymentService.cashIn(request.toCommand(new Id(customerId)));
        return ResponseEntity.ok(TransactionResponse.from(tx));
    }
}