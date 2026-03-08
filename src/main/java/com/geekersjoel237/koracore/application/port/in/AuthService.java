package com.geekersjoel237.koracore.application.port.in;

import com.geekersjoel237.koracore.application.command.LoginCommand;
import com.geekersjoel237.koracore.application.command.RegisterCommand;
import com.geekersjoel237.koracore.domain.model.User;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.domain.vo.Tokens;

public interface AuthService {
    void validatePin(Id customerId, String rawPin);

    void verifyOtp(String email, String code);

    Tokens generateTokens(User user);

    void register(RegisterCommand cmd);

    void login(LoginCommand cmd);

    Tokens verifyOtpAndGetTokens(String email, String code);

    Tokens refresh(String refreshToken);
}