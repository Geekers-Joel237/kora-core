package com.geekersjoel237.koracore.payment.adapters.in.rest.mapping;

import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestConversions {

    public PaymentMethod paymentMethod(String value) {
        return PaymentMethod.fromValue(value);
    }
}
