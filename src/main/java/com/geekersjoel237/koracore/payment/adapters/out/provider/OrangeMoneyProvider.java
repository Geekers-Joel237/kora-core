package com.geekersjoel237.koracore.payment.adapters.out.provider;

import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import org.springframework.stereotype.Component;

@Component
public class OrangeMoneyProvider extends SimulatedPaymentMethodProvider {

    public OrangeMoneyProvider(MobileMoneyPaymentProviderAdapter simulator) {
        super(PaymentMethod.ORANGE_MONEY, simulator);
    }
}
