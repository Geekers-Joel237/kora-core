package com.geekersjoel237.koracore.auth.domain.exception;

import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;

public class UserNotActiveException extends BusinessException {

    public UserNotActiveException(String message) {
        super(message);
    }
}
