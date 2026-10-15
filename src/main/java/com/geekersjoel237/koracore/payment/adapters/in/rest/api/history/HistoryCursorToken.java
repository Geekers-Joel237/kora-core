package com.geekersjoel237.koracore.payment.adapters.in.rest.api.history;

import com.geekersjoel237.koracore.payment.application.query.history.HistoryCursor;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

final class HistoryCursorToken {

    private static final String SEPARATOR = "|";

    private HistoryCursorToken() {
    }

    static String encode(HistoryCursor cursor) {
        String raw = cursor.occurredAt() + SEPARATOR + cursor.transactionId().value();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static HistoryCursor decode(String token) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int separator = raw.indexOf(SEPARATOR);
            return new HistoryCursor(Instant.parse(raw.substring(0, separator)), new Id(raw.substring(separator + 1)));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid cursor: " + token);
        }
    }
}
