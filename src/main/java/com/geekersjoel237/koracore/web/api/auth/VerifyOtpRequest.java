package com.geekersjoel237.koracore.web.api.auth;

public record VerifyOtpRequest(String email, String code) {}