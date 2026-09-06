package com.geekersjoel237.koracore.auth.ports.out.security;

import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.domain.vo.RefreshToken;
import com.geekersjoel237.koracore.auth.domain.vo.Tokens;
import com.geekersjoel237.koracore.shared.domain.vo.Id;


public interface TokenIssuer {

    Tokens issue(User user);


    Id subjectOf(RefreshToken refreshToken);
}
