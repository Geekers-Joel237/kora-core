package com.geekersjoel237.koracore.auth.domain.exception;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;

public class MailDeliveryException extends BusinessException {

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}