package com.geekersjoel237.koracore.domain.exception;

/**
 * Created on 13/03/2026
 *
 * @author Geekers_Joel237
 **/
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
