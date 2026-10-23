package com.gudo.germs.ui.auth;

import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.function.Consumer;

/** Split login screen: red brand panel and a sign-in form. Also offers guest access. */
public final class LoginPanel extends JPanel {
    private final AppContext ctx;
    private final Consumer<Profile> onLogin;
    private final JTextField email = new JTextField(24);
    private final JPasswordField password = new JPasswordField(24);
    private final JButton signIn = new JButton("Sign in");
    private final JLabel message = new JLabel(" ");

    public LoginPanel(AppContext ctx, Consumer<Profile> onLogin, Runnable onGuest) {
        this.ctx = ctx;
        this.onLogin = onLogin;
        setLayout(new java.awt.GridLayout(1, 2));
        add(buildBrandPanel());
        add(buildFormPanel(onGuest));
    }

    private JPanel buildBrandPanel() {
        JPanel p = new JPanel();
        p.setBackground(GermsColors.PRIMARY_RED);
        p.setLayout(new GridBagLayout());
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JLabel name = white("GERMS", 54, Font.BOLD);
        JLabel full = white("Gudo Emergency Response Management System", 16, Font.PLAIN);
        JLabel org = white("Philippine Red Cross \u2013 Davao City Chapter", 13, Font.PLAIN);
        JLabel note = white("Unofficial student project. Not endorsed by the organization.", 11, Font.ITALIC);
        for (JLabel l : new JLabel[] {name, full, org, note}) { l.setAlignmentX(LEFT_ALIGNMENT); box.add(l); box.add(Box.createVerticalStrut(8)); }
        p.add(box);
        return p;
    }

    private static JLabel white(String text, int size, int style) {
        JLabel l = new JLabel(text);
        l.setForeground(Color.WHITE);
        l.setFont(l.getFont().deriveFont(style, (float) size));
        return l;
    }

    private JPanel buildFormPanel(Runnable onGuest) {
        JPanel outer = new JPanel(new GridBagLayout());
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        JLabel title = new JLabel("Sign in");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setAlignmentX(LEFT_ALIGNMENT);
        form.add(title);
        form.add(Box.createVerticalStrut(16));
        form.add(labeled("Email", email));
        form.add(Box.createVerticalStrut(10));
        form.add(labeled("Password", password));
        form.add(Box.createVerticalStrut(16));

        signIn.setBackground(GermsColors.PRIMARY_RED);
        signIn.setForeground(Color.WHITE);
        signIn.setFont(signIn.getFont().deriveFont(Font.BOLD));
        signIn.setAlignmentX(LEFT_ALIGNMENT);
        signIn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        signIn.addActionListener(e -> attemptLogin());
        password.addActionListener(e -> attemptLogin());
        form.add(signIn);

        form.add(Box.createVerticalStrut(8));
        JButton guest = new JButton("Continue as guest");
        guest.setAlignmentX(LEFT_ALIGNMENT);
        guest.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        guest.addActionListener(e -> onGuest.run());
        form.add(guest);

        form.add(Box.createVerticalStrut(12));
        message.setAlignmentX(LEFT_ALIGNMENT);
        form.add(message);
        if (!ctx.config.isConfigured()) {
            showMessage("Supabase is not configured. See application.properties.example.", true);
        }
        outer.add(form, new GridBagConstraints());
        return outer;
    }

    private JPanel labeled(String text, java.awt.Component field) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setAlignmentX(LEFT_ALIGNMENT);
        JLabel l = new JLabel(text);
        l.setForeground(GermsColors.SECONDARY_TEXT);
        p.add(l, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        return p;
    }

    private void attemptLogin() {
        signIn.setEnabled(false);
        showMessage("Signing in\u2026", false);
        String user = email.getText();
        char[] pw = password.getPassword();
        BackgroundTask.run(() -> {
            try { return ctx.auth.login(user, new String(pw)); }
            finally { java.util.Arrays.fill(pw, '\0'); }
        }, profile -> {
            signIn.setEnabled(true);
            password.setText("");
            showMessage(" ", false);
            onLogin.accept(profile);
        }, err -> {
            signIn.setEnabled(true);
            showMessage(friendly(err), true);
        });
    }

    private static String friendly(Throwable t) {
        String m = t.getMessage() == null ? "Sign-in failed." : t.getMessage();
        return m.toLowerCase().contains("invalid login") ? "Incorrect email or password." : m;
    }

    private void showMessage(String text, boolean error) {
        message.setText("<html><body style='width:260px'>" + text.replace("<", "&lt;") + "</body></html>");
        message.setForeground(error ? GermsColors.CRITICAL : GermsColors.SECONDARY_TEXT);
    }
}
