package com.geekersjoel237.koracore.web.exception;

import com.geekersjoel237.koracore.domain.exception.*;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onValidationFailure(MethodArgumentNotValidException ex,
                                      HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");

        List<Map<String, String>> violations = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> Map.of(
                        "field",   error.getField(),
                        "message", error.getDefaultMessage() != null
                                   ? error.getDefaultMessage()
                                   : "Invalid value"
                ))
                .toList();

        detail.setProperty("violations", violations);
        return detail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail onUnreadableMessage(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST,
                "Malformed request body: " + ex.getMostSpecificCause().getMessage());
    }

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
    ProblemDetail onUnprocessable(BusinessException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
    }

    @ExceptionHandler(MailDeliveryException.class)
    ProblemDetail onMailDelivery(MailDeliveryException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(TransientPaymentException.class)
    ProblemDetail onTransient(TransientPaymentException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail onBadRequest(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}