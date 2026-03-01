package com.geekersjoel237.koracore.application.port.in;

import com.geekersjoel237.koracore.application.command.LoginCommand;
import com.geekersjoel237.koracore.application.command.RegisterCommand;
import com.geekersjoel237.koracore.domain.model.User;
import com.geekersjoel237.koracore.domain.vo.Id;
import com.geekersjoel237.koracore.domain.vo.Tokens;

public interface AuthService {
    void validatePin(Id customerId, String rawPin);
    String generateOtp(String email);
    void verifyOtp(String email, String code);
    Tokens generateTokens(User user);

    /** Creates user + customer, sends OTP. Returns the OTP code for testability. */
    String register(RegisterCommand cmd);

    /** Validates PIN then generates OTP for 2-FA. Returns the OTP code. */
    String login(LoginCommand cmd);

    /** Verifies OTP and returns new tokens. */
    Tokens verifyOtpAndGetTokens(String email, String code);

    /** Parses the refresh JWT and issues new token pair. */
    Tokens refresh(String refreshToken);
}