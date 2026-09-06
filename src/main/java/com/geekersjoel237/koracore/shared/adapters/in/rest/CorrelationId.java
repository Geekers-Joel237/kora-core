package com.geekersjoel237.koracore.shared.adapters.in.rest;

import com.geekersjoel237.koracore.shared.domain.vo.Id;

public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";

    private CorrelationId() {
    }

    public static Id fromHeaderOrNew(String header) {
        return header == null || header.isBlank() ? Id.generate() : new Id(header.trim());
    }
}
