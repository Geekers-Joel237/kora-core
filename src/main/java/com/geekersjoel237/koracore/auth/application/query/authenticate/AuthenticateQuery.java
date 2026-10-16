package com.geekersjoel237.koracore.auth.application.query.authenticate;

import com.geekersjoel237.koracore.auth.domain.vo.AccessToken;
import com.geekersjoel237.koracore.auth.domain.vo.AuthenticatedUser;
import com.geekersjoel237.koracore.shared.application.cqrs.Query;

public record AuthenticateQuery(AccessToken accessToken) implements Query<AuthenticatedUser> {
}
