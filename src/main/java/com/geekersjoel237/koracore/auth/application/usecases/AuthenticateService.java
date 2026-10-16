package com.geekersjoel237.koracore.auth.application.usecases;

import com.geekersjoel237.koracore.auth.application.query.authenticate.AuthenticateQuery;
import com.geekersjoel237.koracore.auth.domain.vo.AuthenticatedUser;
import com.geekersjoel237.koracore.auth.ports.in.AuthenticateQueryHandler;
import com.geekersjoel237.koracore.auth.ports.out.security.TokenIssuer;


public final class AuthenticateService implements AuthenticateQueryHandler {

    private final TokenIssuer tokenIssuer;

    public AuthenticateService(TokenIssuer tokenIssuer) {
        this.tokenIssuer = tokenIssuer;
    }

    @Override
    public AuthenticatedUser execute(AuthenticateQuery query) {
        return tokenIssuer.verify(query.accessToken()).authenticatedUser();
    }
}
