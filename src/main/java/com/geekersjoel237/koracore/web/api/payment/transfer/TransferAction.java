package com.geekersjoel237.koracore.web.api.payment.transfer;

import com.geekersjoel237.koracore.application.port.in.PaymentUseCase;
import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.web.api.payment.shared.TransactionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferAction implements TransferApi {

    private final PaymentUseCase paymentUseCase;

    public TransferAction(PaymentUseCase paymentUseCase) {
        this.paymentUseCase = paymentUseCase;
    }

    @Override
    public ResponseEntity<TransactionResponse> transfer(String customerId, TransferRequest request) {
        Transaction tx = paymentUseCase.transfer(request.toCommand(new Id(customerId)));
        return ResponseEntity.ok(TransactionResponse.from(tx));
    }
}