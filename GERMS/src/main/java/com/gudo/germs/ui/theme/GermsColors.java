package com.gudo.germs.ui.theme;

import java.awt.Color;


public final class GermsColors {
    private GermsColors() { }

    public static final Color PRIMARY_RED = Color.decode("#C8102E");
    public static final Color DARK_RED = Color.decode("#8F1025");
    public static final Color LIGHT_RED = Color.decode("#FDECEF");
    public static final Color SUCCESS = Color.decode("#218838");
    public static final Color WARNING = Color.decode("#E6A700");
    public static final Color CRITICAL = Color.decode("#D62828");
    public static final Color INFO = Color.decode("#2474C7");
    public static final Color SECONDARY_TEXT = Color.decode("#747B87");

    public static final Color DARK_BACKGROUND = Color.decode("#15171C");
    public static final Color DARK_SURFACE = Color.decode("#20232B");
    public static final Color DARK_TEXT = Color.decode("#F3F4F6");
    public static final Color LIGHT_BACKGROUND = Color.decode("#F5F6F8");
    public static final Color LIGHT_SURFACE = Color.decode("#FFFFFF");
    public static final Color LIGHT_TEXT = Color.decode("#202124");


    public static Color background() { return ThemeManager.isDark() ? DARK_BACKGROUND : LIGHT_BACKGROUND; }
    public static Color surface() { return ThemeManager.isDark() ? DARK_SURFACE : LIGHT_SURFACE; }
    public static Color text() { return ThemeManager.isDark() ? DARK_TEXT : LIGHT_TEXT; }
    public static Color border() { return ThemeManager.isDark() ? Color.decode("#343843") : Color.decode("#E1E3E8"); }

    public static Color forSeverity(String severity) {
        return switch (severity == null ? "" : severity) {
            case "critical" -> CRITICAL;
            case "high" -> Color.decode("#E8590C");
            case "moderate" -> WARNING;
            default -> INFO;
        };
    }

    public static Color forIncidentStatus(String status) {
        return switch (status == null ? "" : status) {
            case "draft", "archived" -> SECONDARY_TEXT;
            case "published", "acknowledged" -> INFO;
            case "mobilizing" -> WARNING;
            case "active_response" -> CRITICAL;
            case "stabilized", "closed" -> SUCCESS;
            default -> SECONDARY_TEXT;
        };
    }
}
