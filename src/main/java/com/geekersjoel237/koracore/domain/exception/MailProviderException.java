package com.geekersjoel237.koracore.domain.exception;

public class MailProviderException extends Throwable {
    public MailProviderException(Throwable e) {
        super(e);
    }

    public MailProviderException(String message) {
        super(message);
    }
}
