package com.geekersjoel237.koracore.domain.port;

import com.geekersjoel237.koracore.domain.vo.Otp;

import java.util.Optional;

public interface OtpStore {
    void save(String key, Otp otp);
    Optional<Otp> get(String key);
    void delete(String key);
}