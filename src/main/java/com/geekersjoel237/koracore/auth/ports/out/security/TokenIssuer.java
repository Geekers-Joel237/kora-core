package com.geekersjoel237.koracore.auth.ports.out.security;

import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.domain.vo.RefreshToken;
import com.geekersjoel237.koracore.auth.domain.vo.Tokens;
import com.geekersjoel237.koracore.shared.domain.vo.Id;


public interface TokenIssuer {

    Tokens issue(User user);

    /**
     * The user a live refresh token was issued to. Anything else — an access token, a forged
     * or expired one — is refused with {@code InvalidTokenException}. How a refresh token is
     * told apart is the issuer's business.
     */
    Id holderOf(RefreshToken refreshToken);
}
