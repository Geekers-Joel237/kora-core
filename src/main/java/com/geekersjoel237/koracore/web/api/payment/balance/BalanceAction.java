package com.geekersjoel237.koracore.web.api.payment.balance;

import com.geekersjoel237.koracore.application.port.in.PaymentUseCase;
import com.geekersjoel237.koracore.domain.vo.Id;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BalanceAction implements BalanceApi {

    private final PaymentUseCase paymentUseCase;

    public BalanceAction(PaymentUseCase paymentUseCase) {
        this.paymentUseCase = paymentUseCase;
    }

    @Override
    public ResponseEntity<BalanceResponse> getBalance(String customerId) {
        var account = paymentUseCase.getBalance(new Id(customerId));
        return ResponseEntity.ok(BalanceResponse.from(account));
    }
}