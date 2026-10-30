package com.gudo.germs.ui;

import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.security.AccessPolicy;
import com.gudo.germs.security.Module;
import com.gudo.germs.ui.admin.DashboardPanel;
import com.gudo.germs.ui.components.ModulePlaceholderPanel;
import com.gudo.germs.ui.components.SurfacePanel;
import com.gudo.germs.ui.guest.GuestReportPanel;
import com.gudo.germs.ui.guest.SettingsPanel;
import com.gudo.germs.ui.incident.IncidentPanel;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.ui.theme.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Signed-in frame content: top bar, role-filtered sidebar, and lazily created module panels. */
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
        add(buildSidebar(modules), BorderLayout.WEST);
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
        right.add(Box.createHorizontalStrut(16));
        right.add(out);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JComponent buildSidebar(List<Module> modules) {
        JPanel list = new SurfacePanel(null, GermsColors::surface);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));
        ButtonGroup group = new ButtonGroup();
        for (Module m : modules) {
            JToggleButton b = new JToggleButton(m.title());
            b.putClientProperty("JButton.buttonType", "toolBarButton");
            b.setHorizontalAlignment(JToggleButton.LEFT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            b.setPreferredSize(new Dimension(220, 38));
            b.setAlignmentX(LEFT_ALIGNMENT);
            b.setSelected(m == modules.get(0));
            b.addActionListener(e -> show(m));
            group.add(b);
            list.add(b);
            list.add(Box.createVerticalStrut(2));
        }
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    private void show(Module module) {
        built.computeIfAbsent(module, m -> {
            JComponent panel = create(m);
            content.add(panel, m.name());
            return panel;
        });
        cards.show(content, module.name());
    }

    private JComponent create(Module module) {
        return switch (module) {
            case DASHBOARD -> new DashboardPanel(ctx, user);
            case SETTINGS -> new SettingsPanel(user);
            case INCIDENTS -> new IncidentPanel(user);
            case GUEST_REPORT -> new GuestReportPanel(user);
            default -> new ModulePlaceholderPanel(module);
        };
    }
}
