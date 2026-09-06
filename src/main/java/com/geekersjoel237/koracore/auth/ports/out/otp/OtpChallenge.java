package com.geekersjoel237.koracore.auth.ports.out.otp;

import com.geekersjoel237.koracore.auth.domain.enums.OtpPurpose;
import com.geekersjoel237.koracore.auth.domain.vo.OtpCode;


public interface OtpChallenge {


    void issue(String email, OtpPurpose purpose);

    void consume(String email, OtpCode code);
}
