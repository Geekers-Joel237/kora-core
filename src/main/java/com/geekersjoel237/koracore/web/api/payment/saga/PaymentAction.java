package com.geekersjoel237.koracore.web.api.payment.saga;

import com.geekersjoel237.koracore.application.port.in.PaymentUseCase;
import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.web.api.payment.shared.TransactionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentAction implements PaymentApi {

    private final PaymentUseCase paymentUseCase;

    public PaymentAction(PaymentUseCase paymentUseCase) {
        this.paymentUseCase = paymentUseCase;
    }

    @Override
    public ResponseEntity<TransactionResponse> pay(String customerId, PaymentRequest request) {
        Transaction tx = paymentUseCase.executePaymentSaga(request.toCommand(new Id(customerId)));
        return ResponseEntity.ok(TransactionResponse.from(tx));
    }
}