package com.geekersjoel237.koracore.web.api.payment.authorize;

import com.geekersjoel237.koracore.application.port.in.PaymentUseCase;
import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.web.api.payment.shared.TransactionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthorizeAction implements AuthorizeApi {

    private final PaymentUseCase paymentUseCase;

    public AuthorizeAction(PaymentUseCase paymentUseCase) {
        this.paymentUseCase = paymentUseCase;
    }

    @Override
    public ResponseEntity<TransactionResponse> authorize(String customerId, AuthorizeRequest request) {
        Transaction tx = paymentUseCase.authorizePayment(request.toCommand(new Id(customerId)));
        return ResponseEntity.ok(TransactionResponse.from(tx));
    }
}