package com.geekersjoel237.koracore.domain.exception;

public class MailProviderException extends RuntimeException {

    public MailProviderException(Throwable cause) {
        super(cause);
    }

    public MailProviderException(String message) {
        super(message);
    }
}
