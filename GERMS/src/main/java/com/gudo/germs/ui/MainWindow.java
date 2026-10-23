package com.gudo.germs.ui;

import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.ui.auth.LoginPanel;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.CardLayout;
import java.awt.Dimension;


public final class MainWindow extends JFrame {
    private static final String LOGIN = "login";
    private static final String SHELL = "shell";

    private final AppContext ctx;
    private final CardLayout layout = new CardLayout();
    private final JPanel root = new JPanel(layout);
    private Shell shell;

    public MainWindow(AppContext ctx) {
        super("GERMS \u2013 Gudo Emergency Response Management System");
        this.ctx = ctx;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 640));
        setSize(1280, 800);
        setLocationRelativeTo(null);
        setContentPane(root);
        root.add(new LoginPanel(ctx, this::showShell, () -> showShell(Profile.guest())), LOGIN);
        layout.show(root, LOGIN);
    }

    private void showShell(Profile user) {
        if (shell != null) root.remove(shell);
        shell = new Shell(ctx, user, this::signOut);
        root.add(shell, SHELL);
        layout.show(root, SHELL);
        root.revalidate();
    }

    private void signOut() {
        BackgroundTask.run(() -> { ctx.auth.logout(); return Boolean.TRUE; }, ok -> showLogin(), err -> showLogin());
    }

    private void showLogin() {
        layout.show(root, LOGIN);
        if (shell != null) { root.remove(shell); shell = null; }
    }
}
