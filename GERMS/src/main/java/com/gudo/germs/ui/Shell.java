package com.gudo.germs.ui;

import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.security.AccessPolicy;
import com.gudo.germs.security.Module;
import com.gudo.germs.ui.admin.*;
import com.gudo.germs.ui.components.EmergencyContactDirectoryPanel;
import com.gudo.germs.ui.components.PublicAnnouncementsPanel;
import com.gudo.germs.ui.components.SurfacePanel;
import com.gudo.germs.ui.guest.GuestReportPanel;
import com.gudo.germs.ui.guest.MySubmittedReportsPanel;
import com.gudo.germs.ui.guest.SettingsPanel;
import com.gudo.germs.ui.incident.*;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.ui.theme.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Signed-in frame content: top bar, role-filtered sidebar, and lazily created
 * operational module panels.
 */
public final class Shell extends JPanel {
    private final AppContext ctx;
    private final Profile user;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<Module, JComponent> built = new EnumMap<>(Module.class);

    public Shell(AppContext ctx, Profile user, Runnable onLogout) {
        super(new BorderLayout());
        this.ctx = ctx;
        this.user = user;
        add(buildTopBar(onLogout), BorderLayout.NORTH);
        List<Module> modules = AccessPolicy.modulesFor(user.role());
        // Keep the sidebar and its toggle together in the WEST region.
        JPanel navigation = new JPanel(new BorderLayout());
        navigation.setOpaque(false);

        JComponent sidebar = buildSidebar(modules);

        JButton toggleSidebar = new JButton("\\u2630");
        toggleSidebar.setFocusPainted(false);
        toggleSidebar.setToolTipText("Hide sidebar");
        toggleSidebar.setPreferredSize(new Dimension(38, 30));

        toggleSidebar.addActionListener(e -> {
            boolean visible = sidebar.isVisible();
            sidebar.setVisible(!visible);

            if (visible) {
                toggleSidebar.setText("\\u00BB");
                toggleSidebar.setToolTipText("Show sidebar");
            } else {
                toggleSidebar.setText("\\u2630");
                toggleSidebar.setToolTipText("Hide sidebar");
            }

            navigation.revalidate();
            navigation.repaint();
        });

        navigation.add(toggleSidebar, BorderLayout.NORTH);
        navigation.add(sidebar, BorderLayout.CENTER);
        add(navigation, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        if (!modules.isEmpty()) show(modules.get(0));
    }

private JPanel buildTopBar(Runnable onLogout) {
        JPanel bar = new SurfacePanel(new BorderLayout(), GermsColors::surface);
        bar.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        JLabel brand = new JLabel("GERMS");
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 24f));
        brand.setForeground(GermsColors.PRIMARY_RED);
        JLabel org = new JLabel("   Philippine Red Cross \u2013 Davao City Chapter");
        org.setForeground(GermsColors.SECONDARY_TEXT);
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.add(brand);
        left.add(org);
        bar.add(left, BorderLayout.WEST);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        JButton theme = new JButton("\u25D0 Theme");
        theme.addActionListener(e -> ThemeManager.toggle());
        JLabel who = new JLabel(user.fullName() + "  \u00B7  " + user.role().label());
        JButton out = new JButton(user.role().name().equals("GUEST") ? "Exit guest mode" : "Sign out");
        out.addActionListener(e -> onLogout.run());
        right.add(theme);
        right.add(Box.createHorizontalStrut(16));
        right.add(who);
