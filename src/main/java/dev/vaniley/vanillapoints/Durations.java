package dev.vaniley.vanillapoints;

import java.util.Locale;
import java.util.OptionalLong;

/**
 * Pure duration parsing and formatting helpers. Kept free of Bukkit so the rules can be unit tested.
 */
final class Durations {
    private Durations() {
    }

    /**
     * Parses values such as {@code 2s}, {@code 500ms}, {@code 5m}, {@code 1h} or a bare number (seconds).
     * Numbers are also accepted and treated as seconds. Returns an empty optional when the text cannot be parsed.
     */
    static OptionalLong parse(Object value) {
        if (value == null) {
            return OptionalLong.empty();
        }
        if (value instanceof Number number) {
            return OptionalLong.of(Math.max(0L, Math.round(number.doubleValue() * 1000.0D)));
        }

        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return OptionalLong.empty();
        }

        long multiplier = 1000L;
        String numberText = text;
        if (text.endsWith("ms")) {
            multiplier = 1L;
            numberText = text.substring(0, text.length() - 2);
        } else if (text.endsWith("s")) {
            multiplier = 1000L;
            numberText = text.substring(0, text.length() - 1);
        } else if (text.endsWith("m")) {
            multiplier = 60_000L;
            numberText = text.substring(0, text.length() - 1);
        } else if (text.endsWith("h")) {
            multiplier = 3_600_000L;
            numberText = text.substring(0, text.length() - 1);
        }

        try {
            double amount = Double.parseDouble(numberText.trim());
            return OptionalLong.of(Math.max(0L, (long) Math.ceil(amount * multiplier)));
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    /**
     * Formats a millisecond duration into a compact human readable string, e.g. {@code 1s}, {@code 45s},
     * {@code 2m}, {@code 2m 5s}.
     */
    static String format(long millis) {
        if (millis < 1000L) {
            return "1s";
        }

        long seconds = (millis + 999L) / 1000L;
        if (seconds < 60L) {
            return seconds + "s";
        }

        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        if (remainingSeconds == 0L) {
            return minutes + "m";
        }
        return minutes + "m " + remainingSeconds + "s";
    }
}
