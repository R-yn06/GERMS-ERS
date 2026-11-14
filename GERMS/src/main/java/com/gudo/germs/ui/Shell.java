package com.gudo.germs.ui;

import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.models.Role;
import com.gudo.germs.security.AccessPolicy;
import com.gudo.germs.security.Module;
import com.gudo.germs.services.AlarmService;
import com.gudo.germs.utils.BackgroundTask;
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
import com.fasterxml.jackson.databind.JsonNode;

/** Signed-in frame content: top bar, role-filtered sidebar, and lazily created operational module panels. */
public final class Shell extends JPanel {
    private final AppContext ctx;
    private final Profile user;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<Module, JComponent> built = new EnumMap<>(Module.class);
    private final AlarmService alarms = AlarmService.get();
    private final Timer alertWatcher = new Timer(5000, e -> checkEmergencyAlerts());

    public Shell(AppContext ctx, Profile user, Runnable onLogout) {
        super(new BorderLayout());
        this.ctx = ctx;
        this.user = user;
        add(buildTopBar(onLogout), BorderLayout.NORTH);
        List<Module> modules = AccessPolicy.modulesFor(user.role());
        add(buildSidebar(modules), BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        if (!modules.isEmpty()) show(modules.get(0));
        alertWatcher.setCoalesce(true);
    }

    @Override public void addNotify() {
        super.addNotify();
        if (user.role() != Role.GUEST && !alertWatcher.isRunning()) {
            alertWatcher.start();
            checkEmergencyAlerts();
        }
    }

    @Override public void removeNotify() {
        alertWatcher.stop();
        alarms.stopAlarm();
        super.removeNotify();
    }

    private void checkEmergencyAlerts() {
        BackgroundTask.run(() -> {
            if (user.role() == Role.RESPONDER) {
                JsonNode preference = ctx.rest.select("responder_notification_preferences",
                        "select=deployment_notifications_enabled&profile_id=eq." + user.id());
                if (preference.isArray() && !preference.isEmpty()
                        && !preference.get(0).path("deployment_notifications_enabled").asBoolean(true)) {
                    return null;
                }
            }
            return ctx.rest.select("notifications",
                    "select=id,priority,notification_receipts(acknowledged_at)&recipient_id=eq." +
                            user.id() + "&order=created_at.desc&limit=25");
        }, rows -> {
            if (rows == null || !rows.isArray()) return;
            for (JsonNode notification : rows) {
                JsonNode receipts = notification.path("notification_receipts");
                boolean acknowledged = receipts.isArray() && !receipts.isEmpty()
                        && receipts.get(0).hasNonNull("acknowledged_at");
                if (!acknowledged) {
                    alarms.triggerAlarm(notification.path("id").asText(),
                            notification.path("priority").asText("high"), true, true);
                }
            }
        }, ignored -> { /* The panel will display a connection error when opened. */ });
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
            b.setMargin(new Insets(6, 12, 6, 12));
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            b.setPreferredSize(new Dimension(255, 42));
            b.setAlignmentX(LEFT_ALIGNMENT);
            b.setSelected(m == modules.get(0));
            b.addActionListener(e -> show(m));
            group.add(b);
            list.add(b);
            list.add(Box.createVerticalStrut(3));
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
            case SETTINGS -> new SettingsPanel(ctx, user);
            case INCIDENTS -> new IncidentPanel(ctx, user);
            case DISPATCH -> new DispatchPanel(ctx, user);
            case MAP -> new LiveOperationsMapPanel(ctx, user);
            case USERS -> new UserManagementPanel(ctx);
            case TEAMS -> new TeamManagementPanel(ctx, user);
            case RESOURCES -> new ResourceManagementPanel(ctx, user);
            case EVACUATION -> new EvacuationCenterPanel(ctx, user, false);
            case FIELD_REPORTS -> new FieldSituationReportPanel(ctx, user);
            case COMMUNICATION -> new EmergencyCommunicationPanel(ctx, user);
            case CONTACTS -> new EmergencyContactDirectoryPanel(ctx, user, false);
            case REPORTS -> new ReportsAnalyticsPanel(ctx, user);
            case AUDIT -> new AuditLogPanel(ctx, user);

            // Responder Modules
            case MY_DEPLOYMENTS -> new MyDeploymentsPanel(ctx, user);
            case MY_TEAM -> new MyTeamPanel(ctx, user);

            // Guest Modules
            case GUEST_ADVISORIES -> new PublicAnnouncementsPanel(ctx, user, true);
            case GUEST_REPORT -> new GuestReportPanel(ctx, user);
            case GUEST_MY_REPORTS -> new MySubmittedReportsPanel(ctx, user);
            case GUEST_EVACUATION -> new EvacuationCenterPanel(ctx, user, true);
            case GUEST_CONTACTS -> new EmergencyContactDirectoryPanel(ctx, user, true);
        };
    }
}
