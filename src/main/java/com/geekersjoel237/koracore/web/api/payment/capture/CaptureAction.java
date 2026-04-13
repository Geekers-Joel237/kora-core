package com.geekersjoel237.koracore.web.api.payment.capture;

import com.geekersjoel237.koracore.application.port.in.PaymentUseCase;
import com.geekersjoel237.koracore.domain.model.Transaction;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.web.api.payment.shared.TransactionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CaptureAction implements CaptureApi {

    private final PaymentUseCase paymentUseCase;

    public CaptureAction(PaymentUseCase paymentUseCase) {
        this.paymentUseCase = paymentUseCase;
    }

    @Override
    public ResponseEntity<TransactionResponse> capture(String txId, String customerId,
                                                        CaptureRequest request) {
        Transaction tx = paymentUseCase.capturePayment(
                request.toCommand(new Id(txId), new Id(customerId)));
        return ResponseEntity.ok(TransactionResponse.from(tx));
    }
}