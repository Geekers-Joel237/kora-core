package com.geekersjoel237.koracore.shared.adapters.in.rest.exception;

import com.geekersjoel237.koracore.shared.application.cqrs.CommandReplayedException;
import com.geekersjoel237.koracore.shared.domain.exception.CurrencyMismatchException;
import com.geekersjoel237.koracore.shared.application.transaction.RetryExhaustedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;


@RestControllerAdvice
public class SharedExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onValidationFailure(MethodArgumentNotValidException ex) {
        ProblemDetail detail = ProblemDetails.of(HttpStatus.BAD_REQUEST, "Request validation failed");
        List<Map<String, String>> violations = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() != null
                                ? error.getDefaultMessage()
                                : "Invalid value"))
                .toList();
        detail.setProperty("violations", violations);
        return detail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail onUnreadableMessage(HttpMessageNotReadableException ex) {
        return ProblemDetails.of(HttpStatus.BAD_REQUEST,
                "Malformed request body: " + ex.getMostSpecificCause().getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail onBadRequest(IllegalArgumentException ex) {
        return ProblemDetails.of(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(CommandReplayedException.class)
    ProblemDetail onReplay(CommandReplayedException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    ProblemDetail onCurrencyMismatch(CurrencyMismatchException ex) {
        return ProblemDetails.of(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
    }

    @ExceptionHandler(RetryExhaustedException.class)
    ProblemDetail onRetryExhausted(RetryExhaustedException ex) {
        return ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }
}
