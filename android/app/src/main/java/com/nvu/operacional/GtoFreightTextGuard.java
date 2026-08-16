package com.nvu.operacional;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Strict comparison rules for user-visible freight fields.
 *
 * OCR text is never spell-corrected or compared with accents removed. We tolerate
 * capitalization and repeated whitespace only, then preserve the literal text that
 * was read from the stabilized GTO list. Consequently, "Itapetuna" and
 * "Itapetona" (or an accented/unaccented variant) are different values and cannot
 * silently overwrite each other.
 */
final class GtoFreightTextGuard {
    private GtoFreightTextGuard() {}

    static boolean sameVisibleText(String first, String second) {
        String a = comparisonKey(first);
        String b = comparisonKey(second);
        return !a.isEmpty() && a.equals(b);
    }

    static boolean sameNumericValue(String first, String second) {
        String a = digits(first);
        String b = digits(second);
        return !a.isEmpty() && a.equals(b);
    }

    static String comparisonKey(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
            .replaceAll("\\s+", " ")
            .trim();
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }
}
