package com.geekersjoel237.koracore.shared.adapters.in.rest.exception;

import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.Locale;

public final class ProblemDetails {

    public static final String CATALOG =
            "https://github.com/Geekers-Joel237/kora-core/blob/develop/docs/problems.md";
    public static final String CODE = "code";

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatus status, BusinessException ex) {
        return of(status, ex.code(), ex.getMessage());
    }

    public static ProblemDetail of(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(typeOf(code));
        problem.setProperty(CODE, code);
        return problem;
    }

    public static URI typeOf(String code) {
        return URI.create(CATALOG + "#" + code.toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
