package com.geekersjoel237.koracore.auth.ports.out.otp;

import com.geekersjoel237.koracore.auth.domain.enums.OtpPurpose;
import com.geekersjoel237.koracore.auth.domain.exception.InvalidOtpException;
import com.geekersjoel237.koracore.auth.domain.exception.MailDeliveryException;
import com.geekersjoel237.koracore.auth.domain.exception.OtpExpiredException;
import com.geekersjoel237.koracore.auth.domain.vo.OtpCode;


public interface OtpChallenge {


    void issue(String email, OtpPurpose purpose) throws MailDeliveryException;

    void consume(String email, OtpCode code) throws InvalidOtpException, OtpExpiredException;
}
