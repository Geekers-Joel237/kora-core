package com.geekersjoel237.koracore.web.exception;

import com.geekersjoel237.koracore.domain.exception.AccountBlockedException;
import com.geekersjoel237.koracore.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.domain.exception.AccountSuspendedException;
import com.geekersjoel237.koracore.domain.exception.CurrencyMismatchException;
import com.geekersjoel237.koracore.domain.exception.CustomerNotFoundException;
import com.geekersjoel237.koracore.domain.exception.DuplicateEmailException;
import com.geekersjoel237.koracore.domain.exception.InsufficientFundsException;
import com.geekersjoel237.koracore.domain.exception.InvalidAccountException;
import com.geekersjoel237.koracore.domain.exception.InvalidOtpException;
import com.geekersjoel237.koracore.domain.exception.InvalidStateTransitionException;
import com.geekersjoel237.koracore.domain.exception.OtpExpiredException;
import com.geekersjoel237.koracore.domain.exception.PinValidationException;
import com.geekersjoel237.koracore.domain.exception.SelfTransferException;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    ProblemDetail onDuplicateEmail(DuplicateEmailException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({InvalidOtpException.class, OtpExpiredException.class, PinValidationException.class, JwtException.class})
    ProblemDetail onUnauthorized(RuntimeException ex) {
        return problem(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler({CustomerNotFoundException.class, AccountNotFoundException.class})
    ProblemDetail onNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({InsufficientFundsException.class, AccountBlockedException.class,
            AccountSuspendedException.class, SelfTransferException.class,
            CurrencyMismatchException.class, InvalidAccountException.class,
            InvalidStateTransitionException.class})
    ProblemDetail onUnprocessable(RuntimeException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail onBadRequest(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}