package com.geekersjoel237.koracore.payment.domain.exception;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;

public class SelfTransferException extends BusinessException {

    public SelfTransferException(String message) {
        super(message);
    }
}