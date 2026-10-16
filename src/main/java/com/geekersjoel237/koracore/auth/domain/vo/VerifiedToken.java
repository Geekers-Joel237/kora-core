package com.geekersjoel237.koracore.auth.domain.vo;

import com.geekersjoel237.koracore.auth.domain.enums.TokenUse;
import com.geekersjoel237.koracore.auth.domain.exception.TokenUseMismatchException;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.util.Objects;


public record VerifiedToken(Id subject, TokenUse use) {

    public VerifiedToken {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(use, "use");
    }

    public Id subjectFor(TokenUse expected) {
        if (use != expected)
            throw new TokenUseMismatchException(expected);
        return subject;
    }
}
