package com.geekersjoel237.koracore.auth.ports.out.security;

import com.geekersjoel237.koracore.shared.domain.vo.Pin;

public interface CustomerPinEncoder {
    String encode(Pin pin);
    boolean matches(Pin pin, String encodedPin);
}