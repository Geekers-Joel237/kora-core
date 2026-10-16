package com.geekersjoel237.koracore.auth.domain.exception;

import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;

public class InvalidRefreshTokenException extends BusinessException {

    public InvalidRefreshTokenException() {
        super("Refresh token is not valid");
    }

    public InvalidRefreshTokenException(Throwable cause) {
        super("Refresh token is not valid", cause);
    }
}
