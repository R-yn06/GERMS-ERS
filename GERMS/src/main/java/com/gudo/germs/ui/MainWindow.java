package com.gudo.germs.ui;

import java.awt.CardLayout;
import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.JPanel;

import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.ui.auth.LoginPanel;
import com.gudo.germs.utils.BackgroundTask;

/** Top-level window; swaps between the login screen and the role-specific shell. */
public final class MainWindow extends JFrame {
    private static final String LOGIN = "login";
    private static final String SHELL = "shell";

    private final AppContext ctx;
    private final CardLayout layout = new CardLayout();
    private final JPanel root = new JPanel(layout);
    private LoginPanel loginPanel;
    private Shell shell;

    public MainWindow(AppContext ctx) {
        super("GERMS \u2013 Gudo Emergency Response Management System");
        this.ctx = ctx;

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1120, 720));
        setSize(1366, 860);
        setLocationRelativeTo(null);
        setContentPane(root);

        // Build fresh LoginPanel synchronized with current ThemeManager
        showLogin();
    }

    private void showShell(Profile user) {
        if (shell != null) {
            root.remove(shell);
        }
        shell = new Shell(ctx, user, this::signOut);
        root.add(shell, SHELL);
        layout.show(root, SHELL);
        root.revalidate();
        root.repaint();
    }

    private void signOut() {
        BackgroundTask.run(
            () -> { 
                ctx.auth.logout(); 
                return Boolean.TRUE; 
            }, 
            ok -> showLogin(), 
            err -> showLogin()
        );
    }

    private void showLogin() {
        // Remove old login panel to discard any stale state and recreate fresh
        if (loginPanel != null) {
            root.remove(loginPanel);
        }
        loginPanel = new LoginPanel(ctx, this::showShell, () -> showShell(Profile.guest()));
        root.add(loginPanel, LOGIN);
        layout.show(root, LOGIN);

        if (shell != null) {
            root.remove(shell);
            shell = null;
        }

        root.revalidate();
        root.repaint();
    }
}