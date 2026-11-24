package com.geekersjoel237.koracore.shared.adapters.in.rest;

public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String ATTRIBUTE = CorrelationId.class.getName();
    public static final String PROPERTY = "correlationId";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }
}
