package com.geekersjoel237.koracore.auth.adapters.in.rest.exception;

import com.geekersjoel237.koracore.auth.domain.exception.*;
import com.geekersjoel237.koracore.shared.adapters.in.rest.exception.ProblemDetails;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    ProblemDetail onDuplicateEmail(DuplicateEmailException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, ex.getMessage());
    }


    @ExceptionHandler({InvalidOtpException.class, OtpExpiredException.class,
            PinValidationException.class, InvalidRefreshTokenException.class,
            JwtException.class})
    ProblemDetail onUnauthorized(RuntimeException ex) {
        return ProblemDetails.of(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    ProblemDetail onNotFound(CustomerNotFoundException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MailDeliveryException.class)
    ProblemDetail onMailDelivery(MailDeliveryException ex) {
        return ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }
}
