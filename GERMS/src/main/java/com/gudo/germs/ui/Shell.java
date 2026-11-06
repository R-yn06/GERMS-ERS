package com.gudo.germs.ui;

import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.security.AccessPolicy;
import com.gudo.germs.security.Module;
import com.gudo.germs.ui.admin.DashboardPanel;
import com.gudo.germs.ui.components.ModulePlaceholderPanel;
import com.gudo.germs.ui.components.OperationalTablePanel;
import com.gudo.germs.ui.components.SurfacePanel;
import com.gudo.germs.ui.guest.GuestReportPanel;
import com.gudo.germs.ui.guest.SettingsPanel;
import com.gudo.germs.ui.incident.IncidentPanel;
import com.gudo.germs.ui.admin.DispatchPanel;
import com.gudo.germs.ui.admin.UserManagementPanel;
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
import java.util.Arrays;

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
            case INCIDENTS -> new IncidentPanel(ctx, user);
            case DISPATCH -> new DispatchPanel(ctx, user);
            case GUEST_REPORT -> new GuestReportPanel(ctx, user);
            case MAP -> new OperationalTablePanel(ctx, "Live Operations Map",
                    "Incident locations and responder locations shared with the command center.",
                    "incidents", "select=incident_no,title,severity,status,location_text,latitude,longitude&order=updated_at.desc",
                    Arrays.asList(
                            new OperationalTablePanel.Column("Incident", "incident_no"),
                            new OperationalTablePanel.Column("Title", "title"),
                            new OperationalTablePanel.Column("Severity", "severity"),
                            new OperationalTablePanel.Column("Status", "status"),
                            new OperationalTablePanel.Column("Location", "location_text"),
                            new OperationalTablePanel.Column("Latitude", "latitude"),
                            new OperationalTablePanel.Column("Longitude", "longitude")));
            case RESPONDERS -> new UserManagementPanel(ctx, true);
            case USERS -> new UserManagementPanel(ctx, false);
            case REPORTS -> new OperationalTablePanel(ctx, "Reports and Analytics",
                    "Operational incident data suitable for review, filtering, and export from the table.",
                    "incidents", "select=incident_no,title,category,severity,status,reported_at,closed_at&order=reported_at.desc",
                    Arrays.asList(
                            new OperationalTablePanel.Column("Incident", "incident_no"),
                            new OperationalTablePanel.Column("Title", "title"),
                            new OperationalTablePanel.Column("Category", "category"),
                            new OperationalTablePanel.Column("Severity", "severity"),
                            new OperationalTablePanel.Column("Status", "status"),
                            new OperationalTablePanel.Column("Reported", "reported_at"),
                            new OperationalTablePanel.Column("Closed", "closed_at")));
            case EVACUATION -> new OperationalTablePanel(ctx, "Evacuation Centers",
                    "Capacity, occupancy, location, and operating status for registered centers.",
                    "evacuation_centers", "select=name,address,capacity,occupancy,status,latitude,longitude&order=name.asc",
                    Arrays.asList(
                            new OperationalTablePanel.Column("Center", "name"),
                            new OperationalTablePanel.Column("Address", "address"),
                            new OperationalTablePanel.Column("Capacity", "capacity"),
                            new OperationalTablePanel.Column("Occupancy", "occupancy"),
                            new OperationalTablePanel.Column("Status", "status"),
                            new OperationalTablePanel.Column("Latitude", "latitude"),
                            new OperationalTablePanel.Column("Longitude", "longitude")));
            case GUEST_EVACUATION -> new OperationalTablePanel(ctx, "Evacuation Centers",
                    "Publicly available center capacity and operating status.",
                    "evacuation_centers", "select=name,address,capacity,occupancy,status&status=neq.closed&order=name.asc",
                    Arrays.asList(
                            new OperationalTablePanel.Column("Center", "name"),
                            new OperationalTablePanel.Column("Address", "address"),
                            new OperationalTablePanel.Column("Capacity", "capacity"),
                            new OperationalTablePanel.Column("Occupancy", "occupancy"),
                            new OperationalTablePanel.Column("Status", "status")));
            case DISASTER -> new OperationalTablePanel(ctx, "Disaster Management",
                    "Relief operations and inventory currently registered in the command database.",
                    "disaster_resources", "select=name,resource_type,quantity,unit,status,updated_at&order=updated_at.desc",
                    Arrays.asList(
                            new OperationalTablePanel.Column("Resource", "name"),
                            new OperationalTablePanel.Column("Type", "resource_type"),
                            new OperationalTablePanel.Column("Quantity", "quantity"),
                            new OperationalTablePanel.Column("Unit", "unit"),
                            new OperationalTablePanel.Column("Status", "status"),
                            new OperationalTablePanel.Column("Updated", "updated_at")));
            default -> new ModulePlaceholderPanel(module);
        };
    }
}
