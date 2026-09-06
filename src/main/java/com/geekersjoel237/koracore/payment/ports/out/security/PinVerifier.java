package com.geekersjoel237.koracore.payment.ports.out.security;

import com.geekersjoel237.koracore.shared.domain.vo.Id;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;


public interface PinVerifier {

    /**
     * @throws com.geekersjoel237.koracore.auth.domain.exception.PinValidationException  wrong PIN
     * @throws com.geekersjoel237.koracore.auth.domain.exception.CustomerNotFoundException  unknown customer
     */
    void verify(Id customerId, Pin pin);
}
