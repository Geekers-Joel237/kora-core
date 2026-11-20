package com.geekersjoel237.koracore.auth.adapters.in.rest.mapping;

import com.geekersjoel237.koracore.auth.domain.vo.OtpCode;
import com.geekersjoel237.koracore.auth.domain.vo.RefreshToken;
import org.springframework.stereotype.Component;

@Component
public class AuthRequestConversions {

    public OtpCode otpCode(String value) {
        return OtpCode.of(value);
    }

    public RefreshToken refreshToken(String value) {
        return RefreshToken.of(value);
    }
}
