package com.geekersjoel237.koracore.auth.domain.vo;

import com.geekersjoel237.koracore.auth.domain.enums.Role;
import com.geekersjoel237.koracore.auth.domain.enums.TokenUse;
import com.geekersjoel237.koracore.auth.domain.exception.TokenUseMismatchException;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.util.Objects;


public record VerifiedToken(Id subject, TokenUse use, Role role) {

    public VerifiedToken {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(use, "use");
        if (use == TokenUse.ACCESS && role == null)
            throw new IllegalArgumentException("An access token carries a role");
        if (use == TokenUse.REFRESH && role != null)
            throw new IllegalArgumentException("A refresh token proves who, nothing more");
    }

    public static VerifiedToken access(Id subject, Role role) {
        return new VerifiedToken(subject, TokenUse.ACCESS, role);
    }

    public static VerifiedToken refresh(Id subject) {
        return new VerifiedToken(subject, TokenUse.REFRESH, null);
    }

    public Id subjectFor(TokenUse expected) {
        if (use != expected)
            throw new TokenUseMismatchException(expected);
        return subject;
    }

    public AuthenticatedUser authenticatedUser() {
        return new AuthenticatedUser(subjectFor(TokenUse.ACCESS), role);
    }
}
