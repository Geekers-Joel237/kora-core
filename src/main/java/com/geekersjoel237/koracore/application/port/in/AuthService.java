package com.geekersjoel237.koracore.application.port.in;

import com.geekersjoel237.koracore.domain.model.User;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.domain.vo.Tokens;

public interface AuthService {
    void validatePin(Id customerId, String rawPin);
    String generateOtp(String email);
    void verifyOtp(String email, String code);
    Tokens generateTokens(User user);
}