package com.geekersjoel237.koracore.domain.vo;

public record PhoneNumber(String prefix, String number) {

    private static final java.util.regex.Pattern DIGITS_ONLY = java.util.regex.Pattern.compile("\\d+");
    private static final java.util.regex.Pattern VALID_PREFIX = java.util.regex.Pattern.compile("\\+\\d{1,4}");

    public PhoneNumber {
        if (prefix == null || prefix.isBlank())
            throw new IllegalArgumentException("PhoneNumber prefix cannot be blank");
        if (!VALID_PREFIX.matcher(prefix).matches())
            throw new IllegalArgumentException("PhoneNumber prefix must match +\\d{1,4} (e.g. +225)");
        if (number == null || number.isBlank())
            throw new IllegalArgumentException("PhoneNumber number cannot be blank");
        if (!DIGITS_ONLY.matcher(number).matches())
            throw new IllegalArgumentException("PhoneNumber number must contain digits only");
        if (number.length() < 8 || number.length() > 15)
            throw new IllegalArgumentException("PhoneNumber number length must be between 8 and 15 characters");
    }

    public static PhoneNumber of(String prefix, String number) {
        return new PhoneNumber(prefix, number);
    }

    public String fullNumber() {
        return prefix + number;
    }
}