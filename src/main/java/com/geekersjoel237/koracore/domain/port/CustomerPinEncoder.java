package com.geekersjoel237.koracore.domain.port;

public interface CustomerPinEncoder {
    String encode(String rawPin);
    boolean matches(String rawPin, String encodedPin);
}