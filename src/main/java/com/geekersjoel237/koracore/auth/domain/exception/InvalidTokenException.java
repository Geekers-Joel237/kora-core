package com.geekersjoel237.koracore.auth.domain.exception;

import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;

public class InvalidTokenException extends BusinessException {

    public InvalidTokenException() {
        super("Token is not valid");
    }
}
