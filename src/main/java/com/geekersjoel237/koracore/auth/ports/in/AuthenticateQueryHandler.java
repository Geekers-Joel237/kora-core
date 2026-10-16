package com.geekersjoel237.koracore.auth.ports.in;

import com.geekersjoel237.koracore.auth.application.query.authenticate.AuthenticateQuery;
import com.geekersjoel237.koracore.auth.domain.vo.AuthenticatedUser;
import com.geekersjoel237.koracore.shared.ports.in.QueryHandler;

public interface AuthenticateQueryHandler extends QueryHandler<AuthenticateQuery, AuthenticatedUser> {
}
