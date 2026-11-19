package com.gudo.germs.ui.auth;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;
import java.util.Arrays;
import java.util.function.Consumer;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.OverlayLayout;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.ui.theme.ThemeManager;
import com.gudo.germs.utils.BackgroundTask;

/**
 * macOS Style Split Login & Authentication Panel for GERMS.
 * - Left side: Brand identity, Red Cross chapter details, operational badges, and initial logo.
 * - Right side: Fully centered, non-clipping Sign-in card with robust bidirectional Light/Dark theme switching.
 * - macOS Liquid Glass Registration Dialog centered with 60 FPS entrance/exit animations.
 */
public final class LoginPanel extends JLayeredPane{
    
    private final java.util.function.Consumer<Boolean> themeListener = dark -> {
        this.isDarkTheme = dark;
        applyTheme();
    };

    private final AppContext ctx;
    private final Consumer<Profile> onLogin;
    private final Runnable onGuest;

    private boolean isDarkTheme = ThemeManager.isDark();
    private Color macAccentColor = GermsColors.PRIMARY_RED; // Philippine Red Cross Red

    // Background and Cards
    private Background3D background3D;
    private GlassCard splitGlassCard;
    private JPanel cardCenterWrapper;

    // Form Controls & Labels (trackable for theme refresh)
    private final JLabel formTitle = new JLabel("Sign In", SwingConstants.CENTER);
    private final JLabel formSubtitle = new JLabel(
            "<html><div style='text-align: center; width: 300px;'>Enter your credentials to access operations</div></html>",
            SwingConstants.CENTER
    );
    private final JLabel emailLabel = new JLabel("Email Address");
    private final JLabel passLabel = new JLabel("Password");
    private final RoundedTextField emailField = new RoundedTextField("name@redcross.org.ph");
    private final RoundedPasswordField passwordField = new RoundedPasswordField();
    private final MacPillButton signInBtn = new MacPillButton("Sign In", true);
    private final MacPillButton registerBtn = new MacPillButton("Create New Account", false);
    private final JButton guestBtn = new JButton("Continue as Guest (Public Mode)");
    private final JLabel inlineErrorLabel = new JLabel(" ", SwingConstants.CENTER);
    private final LoadingSpinner spinner = new LoadingSpinner();

    // Theme Switcher Component
    private final ThemeToggleBar themeToggleBar = new ThemeToggleBar();

    /** Creates a login panel instance. */
    public LoginPanel(AppContext ctx, Consumer<Profile> onLogin, Runnable onGuest) {
        this.ctx = ctx;
        this.onLogin = onLogin;
        this.onGuest = onGuest;

        ThemeManager.addThemeListener(themeListener);     

        setLayout(new OverlayLayout(this));

        // 1. Interactive 3D Background Layer (Pure Java2D)
        background3D = new Background3D(isDarkTheme, macAccentColor);
        add(background3D, Integer.valueOf(0));

        // 2. Translucent Glass Login Card Layer
        cardCenterWrapper = new JPanel(new GridBagLayout());
        cardCenterWrapper.setOpaque(false);

        splitGlassCard = buildSplitGlassCard();
        cardCenterWrapper.add(splitGlassCard, new GridBagConstraints());
        add(cardCenterWrapper, Integer.valueOf(1));

        // Initial theme styling pass
        applyTheme();

        if (!ctx.config.isConfigured()) {
            showError("Supabase configuration missing in application.properties");
        }
    }

    /** Builds split glass card. */
    private GlassCard buildSplitGlassCard() {
        GlassCard card = new GlassCard(22);
        card.setLayout(new GridLayout(1, 2, 0, 0));
        card.setPreferredSize(new Dimension(920, 580));
        card.setMaximumSize(new Dimension(960, 620));

        card.add(buildLeftBrandPanel());
        card.add(buildRightFormPanel());

        return card;
    }

    // ==========================================
    // LEFT PANEL: BRAND DETAILS & INITIAL LOGO
    // ==========================================
    /** Builds left brand panel. */
    private JPanel buildLeftBrandPanel() {
        JPanel left = new JPanel(new GridBagLayout()) {
            /** Paints component. */
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color c1 = new Color(macAccentColor.getRed(), macAccentColor.getGreen(), macAccentColor.getBlue(), 235);
                Color c2 = new Color(Math.max(0, macAccentColor.getRed() - 55), 15, 25, 248);
                g2.setPaint(new GradientPaint(0, 0, c1, getWidth(), getHeight(), c2));
                g2.fill(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth() + 40, getHeight() -2, 22, 22));
                g2.setClip(0, 0, 0, 0);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        left.setOpaque(false);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(36, 40, 36, 40));

        content.add(createLogoComponent());
        content.add(Box.createVerticalStrut(18));

        JLabel brandName = createLabel("GERMS", 36, Font.BOLD, Color.WHITE);
        JLabel systemName = createLabel("Emergency Response Management System", 13, Font.BOLD, new Color(255, 240, 240));
        JLabel orgName = createLabel("Philippine Red Cross \u2013 Davao City Chapter", 12, Font.PLAIN, new Color(255, 220, 220));

        content.add(brandName);
        content.add(Box.createVerticalStrut(4));
        content.add(systemName);
        content.add(Box.createVerticalStrut(4));
        content.add(orgName);
        content.add(Box.createVerticalStrut(24));

        content.add(createBulletBadge("\u25CF 24/7 Operations Command & Rapid Dispatch"));
        content.add(Box.createVerticalStrut(8));
        content.add(createBulletBadge("\u25CF Real-Time Incident Reporting & Responders Map"));
        content.add(Box.createVerticalStrut(8));
        content.add(createBulletBadge("\u25CF Public Evacuation Shelter Live Tracking"));
        content.add(Box.createVerticalStrut(24));

        JLabel note = createLabel("<html><i>Unofficial student project.<br>Dedicated to disaster relief & community response.</i></html>", 11, Font.PLAIN, new Color(255, 210, 210));
        content.add(note);

        left.add(content, new GridBagConstraints());
        return left;
    }

    /** Creates logo component. */ 
    private JComponent createLogoComponent() {
        URL logoUrl = getClass().getResource("/com/gudo/germs/ui/Images/Logo.png");
        if (logoUrl != null) {
            ImageIcon raw = new ImageIcon(logoUrl);
            Image scaled = raw.getImage().getScaledInstance(200, 200, Image.SCALE_SMOOTH);
            JLabel logo = new JLabel(new ImageIcon(scaled));
            logo.setAlignmentX(LEFT_ALIGNMENT);
            return logo;
        }
        JLabel fallback = new JLabel("\u271A", SwingConstants.CENTER);
        fallback.setFont(new Font("SansSerif", Font.BOLD, 46));
        fallback.setForeground(Color.WHITE);
        fallback.setAlignmentX(LEFT_ALIGNMENT);
        return fallback;
    }

    /** Creates bullet badge. */
    private JPanel createBulletBadge(String text) {
        JPanel badge = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        badge.setOpaque(false);
        badge.setAlignmentX(LEFT_ALIGNMENT);
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        l.setForeground(new Color(255, 245, 245));
        badge.add(l);
        return badge;
    }

    // ==========================================
    // RIGHT PANEL: FORM, CONTROLS & THEME TOGGLE
    // ==========================================
    /** Builds right form panel. */
    private JPanel buildRightFormPanel() {
        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);

        // Top Bar: Theme Switcher anchored top-right with comfortable margins
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 24, 16));
        topBar.setOpaque(false);
        topBar.add(themeToggleBar);
        right.add(topBar, BorderLayout.NORTH);

        // Center Area: Form Inputs perfectly centered horizontally and vertically
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(0, 36, 16, 36));
        // Generous width prevents any truncation
        form.setPreferredSize(new Dimension(380, 480));
        form.setMaximumSize(new Dimension(390, 520));

        // Form Headers (Centered)
        formTitle.setFont(new Font("SansSerif", Font.BOLD, 26));
        formTitle.setAlignmentX(CENTER_ALIGNMENT);
        form.add(formTitle);
        form.add(Box.createVerticalStrut(4));

        formSubtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        formSubtitle.setAlignmentX(CENTER_ALIGNMENT);
        form.add(formSubtitle);
        form.add(Box.createVerticalStrut(20));

        // Email Field Block (Aligned to Field Width)
        JPanel emailLabelRow = createAlignedLabelRow(emailLabel);
        form.add(emailLabelRow);
        form.add(Box.createVerticalStrut(4));
        emailField.setAlignmentX(CENTER_ALIGNMENT);
        form.add(emailField);
        form.add(Box.createVerticalStrut(12));

        // Password Field Block (Aligned to Field Width)
        JPanel passLabelRow = createAlignedLabelRow(passLabel);
        form.add(passLabelRow);
        form.add(Box.createVerticalStrut(4));
        passwordField.setAlignmentX(CENTER_ALIGNMENT);
        form.add(passwordField);
        form.add(Box.createVerticalStrut(8));

        // Status / Error row
        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        statusRow.setOpaque(false);
        statusRow.setMaximumSize(new Dimension(360, 24));
        statusRow.setAlignmentX(CENTER_ALIGNMENT);
        statusRow.add(spinner);
        inlineErrorLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusRow.add(inlineErrorLabel);
        form.add(statusRow);
        form.add(Box.createVerticalStrut(8));

        // Primary Action: Sign In
        signInBtn.setAlignmentX(CENTER_ALIGNMENT);
        signInBtn.setMaximumSize(new Dimension(360, 42));
        signInBtn.setPreferredSize(new Dimension(360, 42));
        signInBtn.addActionListener(e -> attemptLogin());
        passwordField.getField().addActionListener(e -> attemptLogin());
        form.add(signInBtn);
        form.add(Box.createVerticalStrut(10));

        // Secondary Action: Create New Account
        registerBtn.setAlignmentX(CENTER_ALIGNMENT);
        registerBtn.setMaximumSize(new Dimension(360, 40));
        registerBtn.setPreferredSize(new Dimension(360, 40));
        registerBtn.addActionListener(e -> showRegisterDialog());
        form.add(registerBtn);
        form.add(Box.createVerticalStrut(12));

        // Tertiary Action: Guest Access (Clean, Centered, No cut-off)
        guestBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        guestBtn.setContentAreaFilled(false);
        guestBtn.setBorderPainted(false);
        guestBtn.setFocusPainted(false);
        guestBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        guestBtn.setAlignmentX(CENTER_ALIGNMENT);
        guestBtn.addActionListener(e -> onGuest.run());
        form.add(guestBtn);

        center.add(form, new GridBagConstraints());
        right.add(center, BorderLayout.CENTER);

        return right;
    }

    /** Creates aligned label row. */
    private JPanel createAlignedLabelRow(JLabel label) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(360, 20));
        row.setAlignmentX(CENTER_ALIGNMENT);
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        row.add(label, BorderLayout.WEST);
        return row;
    }

    // ==========================================
    // THEME HANDLING & HIGH-CONTRAST PALETTE
    // ==========================================
    /** Applies the theme. */
    private void applyTheme() {
        // High-contrast explicitly forced: pure black/slate in light mode, pure white in dark mode
        Color textHeading = isDarkTheme ? new Color(248, 250, 252) : new Color(15, 23, 42);
        Color textMuted   = isDarkTheme ? new Color(203, 213, 225) : new Color(51, 65, 85);
        Color textInput   = isDarkTheme ? Color.WHITE : new Color(15, 23, 42);

        formTitle.setForeground(textHeading);
        formSubtitle.setForeground(textMuted);
        emailLabel.setForeground(textHeading);
        passLabel.setForeground(textHeading);
        guestBtn.setForeground(textMuted);

        // Update fields directly
        emailField.setTheme(isDarkTheme, textInput);
        passwordField.setTheme(isDarkTheme, textInput);
        registerBtn.setTheme(isDarkTheme);
        splitGlassCard.setTheme(isDarkTheme);
        themeToggleBar.refreshThemeState();
        passwordField.getField().setBorder(null);
        passwordField.getField().setOpaque(false);

        if (background3D != null) {
            background3D.updateTheme(isDarkTheme);
        }

        revalidate();
        repaint();
    }

    /** Unregisters the theme listener and pauses background animation when the panel is removed. */
    @Override
    public void removeNotify() {
        ThemeManager.removeThemeListener(themeListener);
        if (background3D != null) {
            background3D.setPaused(true);
        }
        super.removeNotify();
    }

    /** Briefly shakes a component to draw attention to a validation error. */
    private void shakeComponent(JComponent comp) {
        final Point origin = comp.getLocation();
        final int[] offsets = {-14, 14, -10, 10, -6, 6, -2, 2, 0};
        final Timer shakeTimer = new Timer(30, null);
        shakeTimer.addActionListener(new java.awt.event.ActionListener() {
            int step = 0;
            /** Handles the action event raised by the associated control. */
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (step < offsets.length) {
                    comp.setLocation(origin.x + offsets[step], origin.y);
                    comp.repaint();
                    step++;
                } else {
                    comp.setLocation(origin);
                    shakeTimer.stop();
                }
            }
        });
        shakeTimer.start();
    }

    /** Validates the entered credentials and starts an asynchronous sign-in request. */
    private void attemptLogin() {
        String email = emailField.getText().trim();
        char[] pw = passwordField.getPassword();

        if (email.isEmpty() || pw.length == 0) {
            showError("Please enter your email and password.");
            shakeComponent(splitGlassCard);
            return;
        }

        setBusy(true);
        clearError();

        BackgroundTask.run(() -> {
            try {
                return ctx.auth.login(email, new String(pw));
            } finally {
                Arrays.fill(pw, '\0');
            }
        }, profile -> {
            setBusy(false);
            background3D.setPaused(true);
            onLogin.accept(profile);
        }, err -> {
            setBusy(false);
            showError(friendly(err));
            shakeComponent(splitGlassCard);
        });
    }

    /** Displays register dialog. */
    private void showRegisterDialog() {
        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        GlassRegistrationDialog dialog = new GlassRegistrationDialog(parentWindow, isDarkTheme, macAccentColor, (name, mail, phone, pass, onComplete, onError) -> {
            BackgroundTask.run(
                    () -> ctx.auth.register(mail, pass, name, phone),
                    profile -> {
                        onComplete.run();
                        background3D.setPaused(true);
                        onLogin.accept(profile);
                    },
                    err -> onError.accept(err.getMessage())
            );
        });
        dialog.setVisible(true);
    }

    /** Enables or disables the authentication controls while a request is running. */
    private void setBusy(boolean busy) {
        signInBtn.setEnabled(!busy);
        registerBtn.setEnabled(!busy);
        guestBtn.setEnabled(!busy);
        emailField.setEnabled(!busy);
        passwordField.setEnabled(!busy);
        spinner.setActive(busy);
    }

    /** Displays error. */
    private void showError(String msg) {
        inlineErrorLabel.setText(msg);
        inlineErrorLabel.setForeground(new Color(239, 68, 68));
    }

    /** Clears error. */
    private void clearError() {
        inlineErrorLabel.setText(" ");
    }

    /** Converts an authentication failure into a user-friendly message. */
    private static String friendly(Throwable t) {
        String msg = t.getMessage() == null ? "Authentication failed." : t.getMessage();
        if (msg.toLowerCase().contains("invalid login") || msg.toLowerCase().contains("invalid credentials")) {
            return "Incorrect email address or password.";
        }
        return msg;
    }

    /** Creates label. */
    private static JLabel createLabel(String text, int size, int style, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", style, size));
        l.setForeground(color);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    // =========================================================================
    // MACOS LIQUID GLASS REGISTRATION DIALOG (PROPERLY CENTERED)
    // =========================================================================
    /** Defines the contract for registration handler operations. */
    @FunctionalInterface
    public interface RegistrationHandler {
        /** Starts registration with the supplied details and completion/error callbacks. */
        void handle(String name, String email, String phone, String password, Runnable onComplete, Consumer<String> onError);
    }

    /** Presents a dialog for glass registration. */
    private class GlassRegistrationDialog extends JDialog {
        private final GlassCard modalGlassCard = new GlassCard(20);
        private final RoundedTextField nameField = new RoundedTextField("Juan Dela Cruz");
        private final RoundedTextField emailField = new RoundedTextField("juan@redcross.org.ph");
        private final RoundedTextField phoneField = new RoundedTextField("+63 912 345 6789");
        private final RoundedPasswordField passField = new RoundedPasswordField();
        private final RoundedPasswordField passConfirmField = new RoundedPasswordField();
        private final MacPillButton submitBtn = new MacPillButton("Register Account", true);
        private final MacPillButton cancelBtn = new MacPillButton("Cancel", false);
        private final JLabel modalErrorLabel = new JLabel(" ", SwingConstants.CENTER);
        private final LoadingSpinner modalSpinner = new LoadingSpinner();

        private float animProgress = 0.0f;
        private Timer fadeTimer;
        private boolean isClosing = false;
        private final boolean isDark;

        /** Creates a glass registration dialog instance. */
        public GlassRegistrationDialog(Window owner, boolean isDark, Color accent, RegistrationHandler handler) {
            super(owner, ModalityType.APPLICATION_MODAL);
            this.isDark = isDark;
            setUndecorated(true);
            setBackground(new Color(0, 0, 0, 0));

            JPanel scrimPanel = new JPanel(new GridBagLayout()) {
                /** Paints component. */
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    int maxAlpha = isDark ? 140 : 85;
                    int curAlpha = (int) (maxAlpha * animProgress);
                    g2.setColor(new Color(0, 0, 0, Math.max(0, Math.min(255, curAlpha))));
                    g2.fillRect(0, 0, getWidth(), getHeight());

                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            scrimPanel.setOpaque(false);

            modalGlassCard.setTheme(isDark);
            modalGlassCard.setPreferredSize(new Dimension(440, 580));
            modalGlassCard.setLayout(new BorderLayout());

            // Inner panel centered
            JPanel inner = new JPanel();
            inner.setOpaque(false);
            inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
            inner.setBorder(new EmptyBorder(28, 36, 28, 36));

            JLabel title = new JLabel("Create Account", SwingConstants.CENTER);
            title.setFont(new Font("SansSerif", Font.BOLD, 22));
            title.setForeground(isDark ? new Color(248, 250, 252) : new Color(15, 23, 42));
            title.setAlignmentX(CENTER_ALIGNMENT);
            inner.add(title);
            inner.add(Box.createVerticalStrut(4));

            JLabel subtitle = new JLabel(
                    "<html><div style='text-align: center;'>Join the Davao City emergency responder roster</div></html>",
                    SwingConstants.CENTER
            );
            subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
            subtitle.setForeground(isDark ? new Color(203, 213, 225) : new Color(71, 85, 105));
            subtitle.setAlignmentX(CENTER_ALIGNMENT);
            inner.add(subtitle);
            inner.add(Box.createVerticalStrut(16));

            Color textHead = isDark ? new Color(248, 250, 252) : new Color(15, 23, 42);
            Color textIn = isDark ? Color.WHITE : new Color(15, 23, 42);

            nameField.setTheme(isDark, textIn);
            emailField.setTheme(isDark, textIn);
            phoneField.setTheme(isDark, textIn);
            passField.setTheme(isDark, textIn);
            passConfirmField.setTheme(isDark, textIn);
            cancelBtn.setTheme(isDark);

            inner.add(createModalRow("Full Name", nameField, textHead));
            inner.add(Box.createVerticalStrut(8));
            inner.add(createModalRow("Email Address", emailField, textHead));
            inner.add(Box.createVerticalStrut(8));
            inner.add(createModalRow("Phone Number", phoneField, textHead));
            inner.add(Box.createVerticalStrut(8));
            inner.add(createModalRow("Password (min 6 chars)", passField, textHead));
            inner.add(Box.createVerticalStrut(8));
            inner.add(createModalRow("Confirm Password", passConfirmField, textHead));
            inner.add(Box.createVerticalStrut(10));

            JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
            statusRow.setOpaque(false);
            statusRow.add(modalSpinner);
            modalErrorLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
            statusRow.add(modalErrorLabel);
            statusRow.setAlignmentX(CENTER_ALIGNMENT);
            inner.add(statusRow);
            inner.add(Box.createVerticalStrut(10));

            JPanel btnRow = new JPanel(new GridLayout(1, 2, 12, 0));
            btnRow.setOpaque(false);
            btnRow.setMaximumSize(new Dimension(360, 42));
            btnRow.setAlignmentX(CENTER_ALIGNMENT);

            cancelBtn.addActionListener(e -> fadeOutAndDispose());
            submitBtn.addActionListener(e -> {
                String n = nameField.getText().trim();
                String m = emailField.getText().trim();
                String p = phoneField.getText().trim();
                String p1 = new String(passField.getPassword());
                String p2 = new String(passConfirmField.getPassword());

                if (n.isEmpty() || m.isEmpty() || p.isEmpty() || p1.isEmpty()) {
                    setDialogError("All required fields must be completed.");
                    shakeComponent(modalGlassCard);
                    return;
                }
                if (!p1.equals(p2)) {
                    setDialogError("Passwords do not match.");
                    shakeComponent(modalGlassCard);
                    return;
                }
                if (p1.length() < 6) {
                    setDialogError("Password must be at least 6 characters.");
                    shakeComponent(modalGlassCard);
                    return;
                }

                setDialogError("Registering personnel account...");
                modalSpinner.setActive(true);
                submitBtn.setEnabled(false);
                cancelBtn.setEnabled(false);

                handler.handle(n, m, p, p1, this::fadeOutAndDispose, err -> {
                    modalSpinner.setActive(false);
                    submitBtn.setEnabled(true);
                    cancelBtn.setEnabled(true);
                    setDialogError(err);
                    shakeComponent(modalGlassCard);
                });
            });

            btnRow.add(cancelBtn);
            btnRow.add(submitBtn);
            inner.add(btnRow);

            modalGlassCard.add(inner, BorderLayout.CENTER);
            scrimPanel.add(modalGlassCard, new GridBagConstraints());
            setContentPane(scrimPanel);

            pack();
            if (owner != null) {
                setSize(owner.getWidth(), owner.getHeight());
                setLocationRelativeTo(owner);
            }

            addWindowListener(new WindowAdapter() {
                /** Handles the window opened event. */
                @Override
                public void windowOpened(WindowEvent e) {
                    startFadeIn();
                }
            });
        }

        /** Starts fade in. */
        private void startFadeIn() {
            animProgress = 0.0f;
            trySetWindowOpacity(0.0f);

            final long durationMs = 240;
            final long startTime = System.currentTimeMillis();

            if (fadeTimer != null && fadeTimer.isRunning()) {
                fadeTimer.stop();
            }

            fadeTimer = new Timer(16, e -> {
                long elapsed = System.currentTimeMillis() - startTime;
                float rawProgress = Math.min(1.0f, (float) elapsed / durationMs);
                animProgress = 1.0f - (float) Math.pow(1.0f - rawProgress, 3);

                trySetWindowOpacity(animProgress);
                modalGlassCard.repaint();
                getContentPane().repaint();

                if (rawProgress >= 1.0f) {
                    fadeTimer.stop();
                    animProgress = 1.0f;
                    trySetWindowOpacity(1.0f);
                }
            });
            fadeTimer.start();
        }

        /** Fades out the registration dialog before disposing it. */
        public void fadeOutAndDispose() {
            if (isClosing) return;
            isClosing = true;

            if (fadeTimer != null && fadeTimer.isRunning()) {
                fadeTimer.stop();
            }

            final long durationMs = 180;
            final long startTime = System.currentTimeMillis();
            final float startAlpha = animProgress;

            fadeTimer = new Timer(16, e -> {
                long elapsed = System.currentTimeMillis() - startTime;
                float rawProgress = Math.min(1.0f, (float) elapsed / durationMs);
                animProgress = startAlpha * (1.0f - rawProgress * rawProgress);

                trySetWindowOpacity(animProgress);
                getContentPane().repaint();

                if (rawProgress >= 1.0f) {
                    fadeTimer.stop();
                    dispose();
                }
            });
            fadeTimer.start();
        }

        /** Sets window opacity when the current platform supports translucency. */
        private void trySetWindowOpacity(float alpha) {
            float clamped = Math.max(0.0f, Math.min(1.0f, alpha));
            try {
                GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
                if (gd.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
                    setOpacity(clamped);
                }
            } catch (Throwable ignored) {
            }
        }

        /** Creates modal row. */
        private JPanel createModalRow(String labelText, JComponent field, Color labelColor) {
            JPanel p = new JPanel(new BorderLayout(0, 4));
            p.setOpaque(false);
            p.setAlignmentX(CENTER_ALIGNMENT);
            p.setMaximumSize(new Dimension(360, 60));

            JLabel l = new JLabel(labelText);
            l.setFont(new Font("SansSerif", Font.BOLD, 11));
            l.setForeground(labelColor);

            p.add(l, BorderLayout.NORTH);
            p.add(field, BorderLayout.CENTER);
            return p;
        }

        /** Sets the dialog error value. */
        private void setDialogError(String msg) {
            modalErrorLabel.setText(msg);
            modalErrorLabel.setForeground(new Color(239, 68, 68));
        }
    }

    // =========================================================================
    // HIGH-CONTRAST SEGMENTED THEME TOGGLE BAR (LIGHT & DARK GUARANTEED)
    // =========================================================================
    /** Displays controls for switching between light and dark themes. */
    private class ThemeToggleBar extends JPanel {
        private final JButton lightBtn = new JButton("Light");
        private final JButton darkBtn = new JButton("Dark");

        /** Creates a theme toggle bar instance. */
        public ThemeToggleBar() {
            setLayout(new GridLayout(1, 2, 0, 0));
            setOpaque(false);
            setPreferredSize(new Dimension(150, 30));

            configureBtn(lightBtn, false);
            configureBtn(darkBtn, true);

            add(lightBtn);
            add(darkBtn);
            refreshThemeState();
        }

        /** Configures btn. */
        private void configureBtn(JButton btn, boolean targetsDark) {
            btn.setFont(new Font("SansSerif", Font.BOLD, 12));
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> {
                if (isDarkTheme != targetsDark) {
                    ThemeManager.apply(targetsDark);
                }
            });
        }

        /** Refreshes theme state. */
        public void refreshThemeState() {
            if (isDarkTheme) {
                // Dark mode: "Dark" is selected (crisp white), "Light" is muted
                lightBtn.setForeground(new Color(148, 163, 184)); // Muted slate
                darkBtn.setForeground(Color.WHITE);               // Crisp white
            } else {
                // Light mode: "Light" is selected (dark slate), "Dark" is muted charcoal
                lightBtn.setForeground(new Color(15, 23, 42));     // Bold dark slate
                darkBtn.setForeground(new Color(100, 116, 139));   // Readable slate
            }
            repaint();
        }

        /** Paints component. */
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Outer capsule background
            Color pillTrack = isDarkTheme ? new Color(255, 255, 255, 30) : new Color(0, 0, 0, 20);
            g2.setColor(pillTrack);
            g2.fillRoundRect(0, 0, w, h, 16, 16);

            // Border track for clear visibility on white backgrounds
            g2.setColor(isDarkTheme ? new Color(255, 255, 255, 40) : new Color(203, 213, 225));
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 16, 16);

            // Active sliding highlight
            int segmentW = w / 2;
            int activeX = isDarkTheme ? segmentW : 0;
            Color activeBg = isDarkTheme ? new Color(255, 255, 255, 65) : Color.WHITE;
            g2.setColor(activeBg);
            g2.fillRoundRect(activeX, 0, segmentW, h, 16, 16);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =========================================================================
    // ROUNDED TEXT BOXES WITH BULLETPROOF THEME SYNCHRONIZATION
    // =========================================================================
    /** Text field with rounded styling and a built-in placeholder. */
    public static class RoundedTextField extends JPanel {
        private final JTextField field = new JTextField();
        private boolean isDark = false;

        /** Creates a rounded text field instance. */
        public RoundedTextField(String placeholder) {
            setLayout(new BorderLayout());
            setOpaque(false);
            setBorder(new EmptyBorder(4, 14, 4, 14));
            setMaximumSize(new Dimension(360, 42));
            setPreferredSize(new Dimension(360, 42));

            field.setBorder(null);
            field.setOpaque(false);
            field.setFont(new Font("SansSerif", Font.PLAIN, 13));
            add(field, BorderLayout.CENTER);
        }

        /** Returns text. */
        public String getText() { return field.getText(); }
        /** Returns field. */
        public JTextField getField() { return field; }

        /** Applies the selected light/dark theme to this component. */
        public void setTheme(boolean dark, Color fg) {
            this.isDark = dark;
            field.setBorder(null);
            field.setOpaque(false);

            
            // 1. Explicitly force text, caret, and selection colors on the inner Swing field
            field.setForeground(fg);
            field.setCaretColor(fg);
            field.setSelectedTextColor(dark ? Color.WHITE : Color.WHITE);
            field.setSelectionColor(new Color(230, 40, 40, 160));
            
            // 2. Prevent FlatLaf from overriding with cached L&F defaults
            field.putClientProperty("JComponent.outline", null);
            
            // 3. Trigger immediate layout and paint flush
            field.revalidate();
            field.repaint();
            revalidate();
            repaint();
        }

        /** Paints component. */
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Guaranteed solid opaque-feel colors to prevent any bleed-through
            Color bg = isDark ? new Color(24, 32, 47) : new Color(241, 245, 249);
            Color border = field.hasFocus() 
                    ? GermsColors.PRIMARY_RED 
                    : (isDark ? new Color(60, 75, 96) : new Color(203, 213, 225));

            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);

            g2.setColor(border);
            g2.setStroke(new BasicStroke(field.hasFocus() ? 1.8f : 1.0f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Password field with rounded styling and a built-in placeholder. */
    public static class RoundedPasswordField extends JPanel {
        private final JPasswordField field = new JPasswordField();
        private final JButton textToggleBtn = new JButton("Show");
        private boolean isDark = false;
        private boolean isRevealed = false;

        /** Creates a rounded password field instance. */
        public RoundedPasswordField() {
            setLayout(new BorderLayout(8, 0));
            setOpaque(false);
            setBorder(new EmptyBorder(4, 14, 4, 10));
            setMaximumSize(new Dimension(360, 42));
            setPreferredSize(new Dimension(360, 42));

            field.setBorder(null);
            field.setOpaque(false);
            field.setFont(new Font("SansSerif", Font.PLAIN, 13));
            field.setEchoChar('\u2022');
            add(field, BorderLayout.CENTER);

            textToggleBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
            textToggleBtn.setBorder(null);
            textToggleBtn.setContentAreaFilled(false);
            textToggleBtn.setFocusPainted(false);
            textToggleBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            textToggleBtn.addActionListener(e -> {
                isRevealed = !isRevealed;
                if (isRevealed) {
                    field.setEchoChar((char) 0);
                    textToggleBtn.setText("Hide");
                } else {
                    field.setEchoChar('\u2022');
                    textToggleBtn.setText("Show");
                }
            });
            add(textToggleBtn, BorderLayout.EAST);
        }

        /** Returns password. */
        public char[] getPassword() { return field.getPassword(); }
        /** Returns field. */
        public JPasswordField getField() { return field; }

        /** Applies the selected light/dark theme to this component. */
        public void setTheme(boolean dark, Color fg) {
            this.isDark = dark;
            
            // 1. Explicitly force text, caret, and selection colors on the password field
            field.setForeground(fg);
            field.setCaretColor(fg);
            field.setSelectedTextColor(dark ? Color.WHITE : Color.WHITE);
            field.setSelectionColor(new Color(230, 40, 40, 160));
            
            // 2. High-contrast "Show / Hide" toggle button color
            Color toggleFg = dark ? new Color(148, 163, 184) : new Color(71, 85, 105);
            textToggleBtn.setForeground(toggleFg);
            
            // 3. Trigger immediate repaint
            field.revalidate();
            field.repaint();
            textToggleBtn.repaint();
            revalidate();
            repaint();
        }

        /** Paints component. */
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color bg = isDark ? new Color(24, 32, 47) : new Color(241, 245, 249);
            Color border = field.hasFocus() 
                    ? GermsColors.PRIMARY_RED 
                    : (isDark ? new Color(60, 75, 96) : new Color(203, 213, 225));

            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);

            g2.setColor(border);
            g2.setStroke(new BasicStroke(field.hasFocus() ? 1.8f : 1.0f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ==========================================
    // MACOS LIQUID GLASS CARD CONTAINER
    // ==========================================
    /** Rounded translucent card used to group sign-in content. */
    private static class GlassCard extends JPanel {
        private final int radius;
        private boolean isDark = false;

        /** Creates a glass card instance. */
        public GlassCard(int radius) {
            this.radius = radius;
            setOpaque(false);
        }

        /** Applies the selected light/dark theme to this component. */
        public void setTheme(boolean dark) {
            this.isDark = dark;
            repaint();
        }

        /** Paints component. */
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Ambient Shadow
            g2.setColor(new Color(0, 0, 0, isDark ? 110 : 40));
            g2.fillRoundRect(3, 8, w - 6, h - 6, radius, radius);

            // Frosted Surface Tint
            Color glassTint = isDark ? new Color(15, 23, 42, 238) : new Color(255, 255, 255, 245);
            g2.setColor(glassTint);
            g2.fillRoundRect(0, 0, w - 2, h - 2, radius, radius);

            // Specular Border Rim
            GradientPaint rim = new GradientPaint(
                    0, 0, new Color(255, 255, 255, isDark ? 65 : 180),
                    0, h, new Color(255, 255, 255, isDark ? 15 : 40)
            );
            g2.setPaint(rim);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, w - 3, h - 3, radius, radius);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ==========================================
    // MACOS BUTTONS
    // ==========================================
    /** Rounded button styled for the sign-in and registration actions. */
    private class MacPillButton extends JButton {
        private final boolean isPrimary;
        private boolean isDark = false;

        /** Creates a mac pill button instance. */
        public MacPillButton(String text, boolean isPrimary) {
            super(text);
            this.isPrimary = isPrimary;
            setFont(new Font("SansSerif", Font.BOLD, 13));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        /** Applies the selected light/dark theme to this component. */
        public void setTheme(boolean dark) {
            this.isDark = dark;
            repaint();
        }

        /** Paints component. */
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (isPrimary) {
                Color base = isEnabled() ? macAccentColor : new Color(180, 185, 195);
                if (getModel().isPressed()) base = base.darker();
                else if (getModel().isRollover()) base = base.brighter();

                setForeground(Color.WHITE);
                g2.setPaint(new GradientPaint(0, 0, base.brighter(), 0, getHeight(), base));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            } else {
                Color border = isDark ? new Color(71, 85, 105) : new Color(203, 213, 225);
                Color text = isDark ? new Color(241, 245, 249) : new Color(30, 41, 59);
                Color hover = isDark ? new Color(51, 65, 85) : new Color(241, 245, 249);
                Color fill = isDark ? new Color(30, 41, 59, 180) : Color.WHITE;

                setForeground(text);
                g2.setColor(getModel().isRollover() ? hover : fill);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.setColor(border);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ==========================================
    // LOADING SPINNER
    // ==========================================
    /** Animated spinner shown while an authentication request is running. */
    private static class LoadingSpinner extends JComponent {
        private final Timer timer;
        private int angle = 0;
        private boolean active = false;

        /** Creates a loading spinner instance. */
        public LoadingSpinner() {
            setPreferredSize(new Dimension(16, 16));
            timer = new Timer(50, e -> {
                angle = (angle + 30) % 360;
                repaint();
            });
        }

        /** Starts or stops the spinner animation. */
        public void setActive(boolean active) {
            this.active = active;
            if (active) timer.start();
            else timer.stop();
            repaint();
        }

        /** Paints component. */
        @Override
        protected void paintComponent(Graphics g) {
            if (!active) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.rotate(Math.toRadians(angle), getWidth() / 2.0, getHeight() / 2.0);
            g2.setColor(GermsColors.PRIMARY_RED);
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawArc(2, 2, getWidth() - 4, getHeight() - 4, 0, 270);
            g2.dispose();
        }
    }
}