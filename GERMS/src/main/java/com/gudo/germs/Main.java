package com.gudo.germs;

import com.gudo.germs.config.AppContext;
import com.gudo.germs.ui.MainWindow;
import com.gudo.germs.ui.theme.ThemeManager;

import javax.swing.SwingUtilities;

/** Launches the application by creating its shared context and main window. */
public final class Main {
    /** Prevents instantiation of the application launcher. */
    private Main() { }

    /** Starts the application with the supplied command-line arguments. */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ThemeManager.installSaved();
            new MainWindow(AppContext.create()).setVisible(true);
        });

        
    }
}
