package com.gudo.germs.utils;

import java.util.Locale;

/** Provides responder classification codes and their display labels. */
public final class ResponderClassificationCodes {
    /** Prevents instantiation of this utility class. */
    private ResponderClassificationCodes() {}

    /** Maps legacy responder classifications to their canonical code. */
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

    /** Returns the canonical responder classification code. */
    public static String initials(String code) {
        return canonicalCode(code);
    }
}
