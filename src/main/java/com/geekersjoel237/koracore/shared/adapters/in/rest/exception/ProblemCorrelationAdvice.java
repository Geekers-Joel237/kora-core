package com.geekersjoel237.koracore.shared.adapters.in.rest.exception;

import com.geekersjoel237.koracore.shared.adapters.in.rest.CorrelationId;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class ProblemCorrelationAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return ProblemDetail.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        Object correlationId = ((ServletServerHttpRequest) request).getServletRequest().getAttribute(CorrelationId.ATTRIBUTE);
        if (correlationId != null)
            ((ProblemDetail) body).setProperty(CorrelationId.PROPERTY, correlationId);
        return body;
    }
}
