package com.geekersjoel237.koracore.payment.domain.vo;

import java.time.Instant;

public record AuthorizationResult(String providerReference, Instant expiresAt, boolean success) {}