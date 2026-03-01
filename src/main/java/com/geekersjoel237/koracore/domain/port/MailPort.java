package com.geekersjoel237.koracore.domain.port;

public interface MailPort {
    void sendOtp(String toEmail, String otpCode, OtpMailContext context);
}