package com.nvu.operacional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locale-safe monetary parser for values rendered by GTO.
 *
 * Keeps the decimal semantics instead of flattening every visible digit. This is
 * intentionally dependency-free so the same rules can be exercised by the Java
 * regression runner used by release certification.
 */
final class GtoMoneyValue {
    private static final Pattern TOKEN = Pattern.compile("-?\\d[\\d.,]*");
    private static final long MIN_CENTS = 100L * 100L;
    private static final long MAX_CENTS = 100_000_000L * 100L;

    private GtoMoneyValue() {}

    static Long parseCents(String rawValue) {
        if (rawValue == null) return null;
        String compact = rawValue.replaceAll("\\s+", "").trim();
        Matcher matcher = TOKEN.matcher(compact);
        if (!matcher.find()) return null;
        String token = matcher.group();
        if (token.startsWith("-")) return null;

        String normalized = normalizeNumberToken(token);
        if (normalized.isEmpty()) return null;
        try {
            BigDecimal amount = new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() <= 0) return null;
            return amount.movePointRight(2).longValueExact();
        } catch (Exception ignored) {
            return null;
        }
    }

    static Double parseReais(String rawValue) {
        Long cents = parseCents(rawValue);
        return cents == null ? null : cents / 100.0d;
    }

    static String canonical(String rawValue) {
        Long cents = parseCents(rawValue);
        if (cents == null || !isPlausibleCents(cents)) return "";
        return canonicalFromCents(cents);
    }

    static String canonicalFromCents(long cents) {
        if (!isPlausibleCents(cents)) return "";
        long whole = cents / 100L;
        long fractional = Math.abs(cents % 100L);
        // Keep parsing locale-neutral, but always render money in the pt-BR form used by
        // the GTO/NVU UI. This fixes values such as "R$ 5300" while preserving the exact
        // cent amount used by fingerprints, consensus and backend validation.
        return String.format(
            java.util.Locale.ROOT,
            "R$ %s,%02d",
            groupThousands(whole),
            fractional
        );
    }

    private static String groupThousands(long whole) {
        String digits = Long.toString(Math.abs(whole));
        StringBuilder grouped = new StringBuilder(digits.length() + digits.length() / 3);
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) grouped.append('.');
            grouped.append(digits.charAt(i));
        }
        if (whole < 0L) grouped.insert(0, '-');
        return grouped.toString();
    }

    static boolean isPlausibleCents(long cents) {
        return cents >= MIN_CENTS && cents <= MAX_CENTS;
    }

    static String finalValueCompatibilityIssue(String offeredRaw, String finalRaw) {
        Long offeredCents = parseCents(offeredRaw);
        Long finalCents = parseCents(finalRaw);
        if (offeredCents == null || finalCents == null || offeredCents <= 0L || finalCents <= 0L) return null;

        double ratio = finalCents / (double) offeredCents;
        // The old R3.33 bug converted 5.300,00 into 530000 (exactly 100x).
        if (ratio >= 95.0d && ratio <= 105.0d) {
            return "Valor final incompatível com o frete: possível deslocamento de centavos (aprox. 100x).";
        }
        // Keep this deliberately broad to avoid rejecting legitimate game adjustments,
        // while still blocking obviously corrupted OCR/value semantics.
        if (ratio > 20.0d || ratio < 0.05d) {
            return "Valor final incompatível com o valor ofertado do frete.";
        }
        return null;
    }

    private static String normalizeNumberToken(String token) {
        if (token == null || token.isEmpty()) return "";
        int comma = token.lastIndexOf(',');
        int dot = token.lastIndexOf('.');

        if (comma >= 0 && dot >= 0) {
            if (comma > dot) {
                // pt-BR: 5.300,50
                return token.replace(".", "").replace(',', '.');
            }
            // en-US: 5,300.50
            return token.replace(",", "");
        }

        if (comma >= 0) {
            int decimals = token.length() - comma - 1;
            if (decimals == 1 || decimals == 2) {
                return token.replace(".", "").replace(',', '.');
            }
            // 5,300 is treated as a thousands-grouped integer.
            return token.replace(",", "");
        }

        if (dot >= 0) {
            int count = 0;
            for (int i = 0; i < token.length(); i++) if (token.charAt(i) == '.') count++;
            int decimals = token.length() - dot - 1;
            if (count == 1 && (decimals == 1 || decimals == 2)) return token;
            // GTO commonly renders integer payouts as 6.900 / 15.200.
            return token.replace(".", "");
        }

        return token;
    }
}
