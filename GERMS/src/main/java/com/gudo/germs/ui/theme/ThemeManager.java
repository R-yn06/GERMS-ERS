package com.gudo.germs.ui.theme;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

/** 
 * Installs FlatLaf in light or dark mode with GERMS brand defaults, 
 * remembers the choice, and broadcasts theme changes to registered listeners.
 */
public final class ThemeManager {
    private static final Preferences PREFS = Preferences.userNodeForPackage(ThemeManager.class);
    private static final String KEY_DARK = "theme.dark";
    private static boolean dark;
    private static final List<Consumer<Boolean>> listeners = new ArrayList<>();

    /** Creates a theme manager instance. */
    private ThemeManager() { }

    /** Reports whether this theme manager is dark. */
    public static boolean isDark() { 
        return dark; 
    }

    /** Register a callback whenever the theme changes. */
    public static void addThemeListener(Consumer<Boolean> listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /** Removes theme listener. */
    public static void removeThemeListener(Consumer<Boolean> listener) {
        listeners.remove(listener);
    }

    /** Applies the saved light/dark theme preference. */
    public static void installSaved() { 
        apply(PREFS.getBoolean(KEY_DARK, false)); 
    }

    /** Switches between light and dark themes. */
    public static void toggle() {
        apply(!dark);
    }

    /** Safe to call at startup or later; refreshes all open windows and notifies listeners. */
    public static void apply(boolean useDark) {
        dark = useDark;
        PREFS.putBoolean(KEY_DARK, dark);

        FlatLaf.setGlobalExtraDefaults(Map.ofEntries(
                Map.entry("@accentColor", "#C8102E"),
                Map.entry("@background", useDark ? "#15171C" : "#F5F6F8"),
                Map.entry("@foreground", useDark ? "#F3F4F6" : "#202124"),
                Map.entry("Button.arc", "10"),
                Map.entry("Component.arc", "10"),
                Map.entry("TextComponent.arc", "10"),
                Map.entry("ScrollBar.showButtons", "false"),
                Map.entry("Table.rowHeight", "28"),
                Map.entry("TableHeader.height", "32"),
                Map.entry("TabbedPane.tabHeight", "34")));

        if (useDark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }

        UIManager.put("defaultFont", baseFont());
        FlatLaf.updateUI();

        // Notify custom components on the Swing Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            for (Consumer<Boolean> listener : new ArrayList<>(listeners)) {
                try {
                    listener.accept(dark);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    /** Returns the shared base font used by the application interface. */
    private static Font baseFont() {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String family : new String[] {"Inter", "Segoe UI", "Arial"}) {
            if (available.contains(family)) return new Font(family, Font.PLAIN, 14);
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    }
}