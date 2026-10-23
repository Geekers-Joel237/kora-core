package com.geekersjoel237.koracore.auth.adapters.out.security;

import com.geekersjoel237.koracore.auth.ports.out.security.CustomerPinEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import com.geekersjoel237.koracore.shared.domain.vo.Pin;

@Component
public class BCryptCustomerPinEncoder implements CustomerPinEncoder {

    private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder();

    @Override
    public String encode(Pin pin) {
        return delegate.encode(pin.value());
    }

    @Override
    public boolean matches(Pin pin, String encodedPin) {
        return delegate.matches(pin.value(), encodedPin);
    }
}