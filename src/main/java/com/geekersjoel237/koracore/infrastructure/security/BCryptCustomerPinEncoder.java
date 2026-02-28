package com.geekersjoel237.koracore.infrastructure.security;

import com.geekersjoel237.koracore.domain.port.CustomerPinEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptCustomerPinEncoder implements CustomerPinEncoder {

    private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder();

    @Override
    public String encode(String rawPin) {
        return delegate.encode(rawPin);
    }

    @Override
    public boolean matches(String rawPin, String encodedPin) {
        return delegate.matches(rawPin, encodedPin);
    }
}