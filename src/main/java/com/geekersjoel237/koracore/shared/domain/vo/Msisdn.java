package com.geekersjoel237.koracore.shared.domain.vo;

import java.util.regex.Pattern;


public record Msisdn(String value) {

    private static final Pattern DIALABLE = Pattern.compile("\\+\\d{9,19}");

    private static final int VISIBLE_HEAD = 7;
    private static final int VISIBLE_TAIL = 3;

    public Msisdn {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Msisdn cannot be blank");
        if (!DIALABLE.matcher(value).matches())
            throw new IllegalArgumentException(
                    "Msisdn must be a plus sign followed by 9 to 19 digits");
    }

    public static Msisdn of(String value) {
        return new Msisdn(value);
    }

    public String masked() {
        return value.substring(0, VISIBLE_HEAD)
                + "***"
                + value.substring(value.length() - VISIBLE_TAIL);
    }

    @Override
    public String toString() {
        return masked();
    }
}
