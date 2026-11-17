package com.gudo.germs.utils;

import java.util.Locale;

public final class ResponderClassificationCodes {
    private ResponderClassificationCodes() {}

    public static String canonicalCode(String code) {
        if (code == null || code.isBlank()) return "";
        return switch (code.trim().toUpperCase(Locale.ROOT)) {
            case "SAF_AMB", "SAF_FA" -> "EMS";
            case "SAF_ERU" -> "ERU";
            case "SAF_EMR" -> "EMR";
            case "SAF_EMT" -> "EMT";
            case "DMS_SAR", "DMS_FLOOD", "DMS_FIRE", "DMS_ASSESS", "DMS_EVAC", "DMS_RELIEF" -> "DMS";
            default -> code.trim().toUpperCase(Locale.ROOT);
        };
    }

    public static String initials(String code) {
        return canonicalCode(code);
    }
}
