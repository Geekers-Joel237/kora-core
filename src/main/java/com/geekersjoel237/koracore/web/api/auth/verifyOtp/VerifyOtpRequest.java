package com.geekersjoel237.koracore.web.api.auth.verifyOtp;

public record VerifyOtpRequest(String email, String code) {}