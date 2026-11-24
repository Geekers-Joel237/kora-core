package com.geekersjoel237.koracore.shared.adapters.in.rest;

import com.geekersjoel237.koracore.shared.domain.vo.Id;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = correlationIdOf(request);
        if (correlationId == null) {
            chain.doFilter(request, response);
            return;
        }
        request.setAttribute(CorrelationId.ATTRIBUTE, correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);
        MDC.put(CorrelationId.MDC_KEY, correlationId);
        try {
            chain.doFilter(new WithCorrelationId(request, correlationId), response);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }

    private static String correlationIdOf(HttpServletRequest request) {
        String header = request.getHeader(CorrelationId.HEADER);
        if (header != null && !header.isBlank())
            return header.trim();
        return HttpMethod.POST.matches(request.getMethod()) ? Id.generate().value() : null;
    }

    private static final class WithCorrelationId extends HttpServletRequestWrapper {

        private final String correlationId;

        private WithCorrelationId(HttpServletRequest request, String correlationId) {
            super(request);
            this.correlationId = correlationId;
        }

        @Override
        public String getHeader(String name) {
            return CorrelationId.HEADER.equalsIgnoreCase(name) ? correlationId : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return CorrelationId.HEADER.equalsIgnoreCase(name)
                    ? Collections.enumeration(Set.of(correlationId))
                    : super.getHeaders(name);
        }
    }
}
