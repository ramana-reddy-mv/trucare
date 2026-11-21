package com.trucare.shared.observability;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationId {

    public static final String HEADER = "X-Correlation-ID";
    public static final String CONTEXT_KEY = "correlationId";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern VALID_VALUE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private CorrelationId() {
    }

    public static boolean isValid(String value) {
        return value != null && VALID_VALUE.matcher(value).matches();
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }
}
