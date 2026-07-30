package co.wethinkcode.healthsafe;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class WardDataNormalizer {

    private static final Set<String> PLACEHOLDERS = new HashSet<>(Arrays.asList(
            "N/A", "n/a", "TBD", "unknown", "-", "NaN"
    ));

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy")
    };

    /**
     * Returns null if raw is a known placeholder/missing token.
     * Otherwise returns raw unchanged.
     */
    public static String normalizePlaceholder(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.trim().isEmpty() || PLACEHOLDERS.contains(raw)) {
            return null;
        }
        return raw;
    }

    /**
     * Trims, collapses internal double (or more) spaces to one,
     * and applies a consistent Title Case or Upper Case rule.
     */
    public static String normalizeText(String raw) {
        if (raw == null) {
            return null;
        }

        // Clean up spaces
        String cleaned = raw.trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            return "";
        }

        // Special casing for ward IDs (e.g., "w-05" -> "W-05")
        if (cleaned.toLowerCase().matches("^w-\\d+$")) {
            return cleaned.toUpperCase();
        }

        // Standard Title Case for normal text
        String[] words = cleaned.split(" ");
        StringBuilder titleCase = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (!word.isEmpty()) {
                titleCase.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase());
                if (i < words.length - 1) {
                    titleCase.append(" ");
                }
            }
        }
        return titleCase.toString();
    }

    /**
     * Returns null for unparseable/invalid values or negative counts,
     * otherwise returns the parsed integer.
     */
    public static Integer normalizeNumber(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value < 0 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Maps the Y/N/yes/no/1/0/true/FALSE family to Boolean.TRUE / Boolean.FALSE.
     * Returns null if unrecognized.
     */
    public static Boolean normalizeBoolean(String raw) {
        if (raw == null) {
            return null;
        }
        String clean = raw.trim().toLowerCase();
        return switch (clean) {
            case "y", "yes", "true", "1" -> Boolean.TRUE;
            case "n", "no", "false", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    /**
     * Tries known formats in order. Returns null if none match or the date is invalid.
     */
    public static java.time.LocalDate normalizeDate(String raw) {
        if (raw == null) {
            return null;
        }
        String clean = raw.trim();
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(clean, formatter);
            } catch (DateTimeParseException e) {
                // Keep trying remaining formatters
            }
        }
        return null;
    }
}
