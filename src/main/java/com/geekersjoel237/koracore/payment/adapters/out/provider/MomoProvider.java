package com.geekersjoel237.koracore.payment.adapters.out.provider;

import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import org.springframework.stereotype.Component;

@Component
public class MomoProvider extends SimulatedPaymentMethodProvider {

    public MomoProvider(MobileMoneyPaymentProviderAdapter simulator) {
        super(PaymentMethod.MOBILE_MONEY, simulator);
    }
}
