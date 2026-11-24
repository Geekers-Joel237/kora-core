package com.geekersjoel237.koracore.shared.adapters.in.rest;

public final class ApiSchemas {

    public static final String BEARER_AUTH = "bearerAuth";
    public static final String JSON = "application/json";
    public static final String PROBLEM_JSON = "application/problem+json";
    public static final String PROBLEM = "Problem";
    public static final String PROBLEM_REF = "#/components/schemas/" + PROBLEM;

    public static final String AMOUNT = "Whole major units of the currency: XAF, the only currency today, has no minor unit";
    public static final String SIGNED_AMOUNT = AMOUNT + ". Negative when the money leaves the wallet";

    private ApiSchemas() {
    }
}
