package com.geekersjoel237.koracore.auth.domain.exception;

import com.geekersjoel237.koracore.auth.domain.enums.TokenUse;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;


public class TokenUseMismatchException extends BusinessException {

    public TokenUseMismatchException(TokenUse expected) {
        super("Token is not valid for " + expected.name().toLowerCase() + " use");
    }
}
