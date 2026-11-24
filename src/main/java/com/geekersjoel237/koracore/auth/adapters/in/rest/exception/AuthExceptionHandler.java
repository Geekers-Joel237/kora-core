package com.geekersjoel237.koracore.auth.adapters.in.rest.exception;

import com.geekersjoel237.koracore.auth.domain.exception.*;
import com.geekersjoel237.koracore.shared.adapters.in.rest.exception.ProblemDetails;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    ProblemDetail onDuplicateEmail(DuplicateEmailException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler({InvalidOtpException.class, OtpExpiredException.class,
            PinValidationException.class, InvalidTokenException.class})
    ProblemDetail onUnauthorized(BusinessException ex) {
        return ProblemDetails.of(HttpStatus.UNAUTHORIZED, ex);
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail onUnauthenticated() {
        return ProblemDetails.of(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "A valid access token is required");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail onForbidden() {
        return ProblemDetails.of(HttpStatus.FORBIDDEN, "FORBIDDEN", "The token's role does not grant this route");
    }

    @ExceptionHandler(UserNotActiveException.class)
    ProblemDetail onUserNotActive(UserNotActiveException ex) {
        return ProblemDetails.of(HttpStatus.FORBIDDEN, ex);
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    ProblemDetail onNotFound(CustomerNotFoundException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(MailDeliveryException.class)
    ProblemDetail onMailDelivery(MailDeliveryException ex) {
        return ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE, ex);
    }
}
