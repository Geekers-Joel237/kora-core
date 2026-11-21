package com.geekersjoel237.koracore.payment.adapters.out.provider;

import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.enums.ProviderOperationType;
import com.geekersjoel237.koracore.payment.domain.vo.AuthorizationResult;
import com.geekersjoel237.koracore.payment.domain.vo.CaptureResult;
import com.geekersjoel237.koracore.payment.domain.vo.ReverseResult;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.PhoneNumber;

abstract class SimulatedPaymentMethodProvider implements PaymentMethodProvider {

    private final PaymentMethod method;
    private final MobileMoneyPaymentProviderAdapter simulator;

    protected SimulatedPaymentMethodProvider(PaymentMethod method, MobileMoneyPaymentProviderAdapter simulator) {
        this.method = method;
        this.simulator = simulator;
    }

    @Override
    public PaymentMethod method() {
        return method;
    }

    @Override
    public AuthorizationResult authorize(Amount amount, Id correlationId,
                                         ProviderOperationType operationType, PhoneNumber customerPhone) {
        return simulator.authorize(amount, method, correlationId, operationType, customerPhone);
    }

    @Override
    public CaptureResult capture(String authorizationReference, Id correlationId) {
        return simulator.capture(authorizationReference, correlationId);
    }

    @Override
    public ReverseResult reverse(String reference, Amount amount, Id correlationId) {
        return simulator.reverse(reference, amount, correlationId);
    }
}
