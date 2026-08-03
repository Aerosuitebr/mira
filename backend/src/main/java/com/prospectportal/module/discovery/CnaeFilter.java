package com.prospectportal.module.discovery;

import java.util.Arrays;
import java.util.List;

public record CnaeFilter(String mode, String value, List<String> prefixes) {

    public static final String MODE_NONE = "none";
    public static final String MODE_EXACT = "exact";
    public static final String MODE_PREFIX = "prefix";
    public static final String MODE_SECTIONS = "sections";

    public static CnaeFilter none() {
        return new CnaeFilter(MODE_NONE, "", List.of());
    }

    public static CnaeFilter parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return none();
        }

        String value = raw.trim();
        if (value.contains(",")) {
            List<String> prefixes = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
            if (prefixes.isEmpty()) {
                return none();
            }
            return new CnaeFilter(MODE_SECTIONS, value, prefixes);
        }

        if (value.matches("\\d{7}")) {
            return new CnaeFilter(MODE_EXACT, value, List.of());
        }

        if (value.matches("\\d{2,6}")) {
            return new CnaeFilter(MODE_PREFIX, value, List.of());
        }

        return new CnaeFilter(MODE_EXACT, value, List.of());
    }

    public boolean isActive() {
        return !MODE_NONE.equals(mode);
    }

    /** Limite superior exclusivo para range scan em cnae_main (ex.: 33163 → 33164). */
    public String prefixUpperBound() {
        if (!MODE_PREFIX.equals(mode) || value.isBlank()) {
            return value;
        }
        int lastDigit = value.length() - 1;
        while (lastDigit >= 0 && Character.isDigit(value.charAt(lastDigit))) {
            lastDigit--;
        }
        String numericPart = value.substring(lastDigit + 1);
        if (numericPart.isEmpty()) {
            return value + "\u0000";
        }
        long next = Long.parseLong(numericPart) + 1;
        return value.substring(0, lastDigit + 1) + next;
    }
}
