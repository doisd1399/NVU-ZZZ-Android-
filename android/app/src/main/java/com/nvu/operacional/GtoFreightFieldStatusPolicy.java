package com.nvu.operacional;

/** HF22: field-by-field state is diagnostic/UX metadata only; it never changes selection identity. */
final class GtoFreightFieldStatusPolicy {
    static final String CONFIRMED = "CONFIRMED";
    static final String PENDING = "PENDING";
    static final String OPTIONAL = "OPTIONAL";

    private GtoFreightFieldStatusPolicy() {}

    static String required(String value, String pendingField, String field) {
        if (field != null && field.equals(pendingField)) return PENDING;
        return value != null && !value.trim().isEmpty() ? CONFIRMED : PENDING;
    }

    static String optional(String value) {
        return value != null && !value.trim().isEmpty() ? CONFIRMED : OPTIONAL;
    }
}
