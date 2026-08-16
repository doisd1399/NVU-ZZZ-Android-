package com.nvu.operacional;

import java.util.Locale;

/**
 * Field-level review policy used after the selected row identity is already confirmed.
 * Missing text never invalidates the selected row and manual values are never autocorrected.
 */
final class GtoFreightReviewPolicy {
    static final String CARGO = "CARGO";
    static final String ORIGIN_COMPANY = "ORIGIN_COMPANY";
    static final String DESTINATION_COMPANY = "DESTINATION_COMPANY";
    static final String DESTINATION = "DESTINATION";
    static final String DISTANCE = "DISTANCE";
    static final String VALUE = "VALUE";
    static final String LOCAL_INTEGRITY = "LOCAL_INTEGRITY";

    private GtoFreightReviewPolicy() {}

    static String firstRequiredField(
        String cargo,
        String originCompany,
        String destinationCompany,
        String destination,
        String distance,
        String value
    ) {
        if (!validText(cargo)) return CARGO;
        if (!validText(originCompany)) return ORIGIN_COMPANY;
        // HF14: destinationCompany is optional metadata. It may be captured and
        // preserved internally when readable, but it is never a driver-required field.
        if (!validText(destination)) return DESTINATION;
        if (!validDistance(distance)) return DISTANCE;
        if (!validMoney(value)) return VALUE;
        return "";
    }

    static boolean isManualValueValid(String field, String value) {
        if (field == null) return false;
        String key = field.trim().toUpperCase(Locale.ROOT);
        if (DISTANCE.equals(key)) return validDistance(value);
        if (VALUE.equals(key)) return validMoney(value);
        return CARGO.equals(key) || ORIGIN_COMPANY.equals(key) || DESTINATION.equals(key)
            ? validText(value)
            : false;
    }

    static String preserveLiteralManualText(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean validText(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        return trimmed.length() >= 2 && trimmed.length() <= 220;
    }

    private static boolean validDistance(String value) {
        if (value == null) return false;
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return false;
        try {
            int km = Integer.parseInt(digits);
            return km >= 10 && km <= 10_000;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean validMoney(String value) {
        Double parsed = GtoMoneyValue.parseReais(value);
        return parsed != null && parsed >= 100d && parsed <= 100_000_000d;
    }
}
