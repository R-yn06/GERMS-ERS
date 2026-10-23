package com.gudo.germs;

import com.gudo.germs.config.AppContext;
import com.gudo.germs.ui.MainWindow;
import com.gudo.germs.ui.theme.ThemeManager;

import javax.swing.SwingUtilities;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ThemeManager.installSaved();
            new MainWindow(AppContext.create()).setVisible(true);
        });
    }
}
