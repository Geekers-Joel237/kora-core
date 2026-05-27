package com.geekersjoel237.koracore.web.api.payment.reverse;

import com.geekersjoel237.koracore.application.port.in.PaymentUseCase;
import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.web.api.payment.shared.TransactionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReverseAction implements ReverseApi {

    private final PaymentUseCase paymentUseCase;

    public ReverseAction(PaymentUseCase paymentUseCase) {
        this.paymentUseCase = paymentUseCase;
    }

    @Override
    public ResponseEntity<TransactionResponse> reverse(String txId, ReverseRequest request) {
        Transaction tx = paymentUseCase.reversePayment(request.toCommand(new Id(txId)));
        return ResponseEntity.ok(TransactionResponse.from(tx));
    }
}