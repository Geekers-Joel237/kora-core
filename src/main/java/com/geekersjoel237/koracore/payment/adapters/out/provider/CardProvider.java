package com.geekersjoel237.koracore.payment.adapters.out.provider;

import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import org.springframework.stereotype.Component;

@Component
public class CardProvider extends SimulatedPaymentMethodProvider {

    public CardProvider(MobileMoneyPaymentProviderAdapter simulator) {
        super(PaymentMethod.CARD, simulator);
    }
}
