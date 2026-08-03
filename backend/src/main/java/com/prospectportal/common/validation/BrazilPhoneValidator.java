package com.prospectportal.common.validation;

import java.util.Set;

public final class BrazilPhoneValidator {

    private static final Set<Integer> VALID_DDD = Set.of(
        11, 12, 13, 14, 15, 16, 17, 18, 19,
        21, 22, 24, 27, 28,
        31, 32, 33, 34, 35, 37, 38,
        41, 42, 43, 44, 45, 46, 47, 48, 49,
        51, 53, 54, 55,
        61, 62, 63, 64, 65, 66, 67, 68, 69,
        71, 73, 74, 75, 77, 79,
        81, 82, 83, 84, 85, 86, 87, 88, 89,
        91, 92, 93, 94, 95, 96, 97, 98, 99
    );

    private BrazilPhoneValidator() {
    }

    public static String normalizeOptional(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        if (!isValid(phone)) {
            throw new IllegalArgumentException("Telefone inválido. Use DDD + número.");
        }
        return format(nationalDigits(phone));
    }

    public static boolean isValid(String phone) {
        String digits = nationalDigits(phone);
        if (digits.length() != 10 && digits.length() != 11) {
            return false;
        }

        int ddd = Integer.parseInt(digits.substring(0, 2));
        if (!VALID_DDD.contains(ddd)) {
            return false;
        }

        if (digits.length() == 11) {
            return digits.charAt(2) == '9';
        }

        char first = digits.charAt(2);
        return first >= '2' && first <= '5';
    }

    private static String nationalDigits(String phone) {
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("55") && digits.length() > 11) {
            digits = digits.substring(2);
        }
        return digits.length() > 11 ? digits.substring(0, 11) : digits;
    }

    private static String format(String digits) {
        if (digits.length() == 11) {
            return String.format("(%s) %s-%s",
                digits.substring(0, 2),
                digits.substring(2, 7),
                digits.substring(7));
        }
        return String.format("(%s) %s-%s",
            digits.substring(0, 2),
            digits.substring(2, 6),
            digits.substring(6));
    }
}
