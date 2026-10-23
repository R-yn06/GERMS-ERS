package com.gudo.germs.ui.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.UIManager;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

// Installs FlatLaf in light or dark mode and remembers

public final class ThemeManager {
    private static final Preferences PREFS = Preferences.userNodeForPackage(ThemeManager.class);
    private static final String KEY_DARK = "theme.dark";
    private static boolean dark;


    private ThemeManager() { }

    public static boolean isDark() { return dark; }

    public static void installSaved() { apply(PREFS.getBoolean(KEY_DARK, false)); }

    public static void toggle() {
        apply(!dark);
        PREFS.putBoolean(KEY_DARK, dark);
    }

    //refreshes all open windows
    public static void apply(boolean useDark) {
        dark = useDark;
        FlatLaf.setGlobalExtraDefaults(Map.ofEntries(
                Map.entry("@accentColor", "#C8102E"),
                Map.entry("@background", useDark ? "#15171C" : "#F5F6F8"),
                Map.entry("@foreground", useDark ? "#F3F4F6" : "#202124"),
                Map.entry("Button.arc", "10"),
                Map.entry("Component.arc", "10"),
                Map.entry("TextComponent.arc", "10"),
                Map.entry("ScrollBar.showButtons", "false")));
        if (useDark) FlatDarkLaf.setup(); else FlatLightLaf.setup();
        UIManager.put("defaultFont", baseFont());
        FlatLaf.updateUI();
    }

    private static Font baseFont() {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String family : new String[] {"Inter", "Segoe UI", "Arial"}) {
            if (available.contains(family)) return new Font(family, Font.PLAIN, 14);
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    }
}
