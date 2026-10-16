package com.geekersjoel237.koracore.auth.ports.out.security;

import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.domain.vo.RefreshToken;
import com.geekersjoel237.koracore.auth.domain.vo.Tokens;
import com.geekersjoel237.koracore.auth.domain.vo.VerifiedToken;


public interface TokenIssuer {

    Tokens issue(User user);

    /**
     * Checks signature and expiry, and reports the use the token was issued for. Whether that
     * use is acceptable is the caller's decision, not the issuer's.
     */
    VerifiedToken verify(RefreshToken refreshToken);
}
