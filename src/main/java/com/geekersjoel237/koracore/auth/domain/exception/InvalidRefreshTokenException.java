package com.geekersjoel237.koracore.auth.domain.exception;


public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(Throwable cause) {
        super("Refresh token is not valid", cause);
    }
}
