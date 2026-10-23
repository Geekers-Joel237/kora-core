package com.geekersjoel237.koracore.payment.adapters.in.rest.exception;

import com.geekersjoel237.koracore.payment.domain.exception.*;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;
import com.geekersjoel237.koracore.shared.adapters.in.rest.exception.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class PaymentExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail onNotFound(AccountNotFoundException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({InsufficientFundsException.class, AccountBlockedException.class,
            AccountSuspendedException.class, SelfTransferException.class,
            InvalidAccountException.class, InvalidStateTransitionException.class})
    ProblemDetail onUnprocessable(BusinessException ex) {
        return ProblemDetails.of(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
    }
}
