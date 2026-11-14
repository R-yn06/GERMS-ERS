package com.gudo.germs.ui.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Incident;
import com.gudo.germs.models.IncidentStatus;
import com.gudo.germs.models.Profile;
import com.gudo.germs.models.Role;
import com.gudo.germs.services.IncidentService;
import com.gudo.germs.ui.components.OpenStreetMapPanel;
import com.gudo.germs.ui.components.OpenStreetMapPanel.MapMarker;
import com.gudo.germs.ui.components.OpenStreetMapPanel.MarkerType;
import com.gudo.germs.ui.components.StatCard;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Command Dashboard & Central Operations Hub.
 * Streamlines incident statistics, deployed personnel tracking, live OpenStreetMap pinpoints of
 * ongoing incidents and responders, and an integrated broadcast console for administrator announcements.
 */
public final class DashboardPanel extends JPanel {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MMM dd, HH:mm").withZone(ZoneId.systemDefault());

    private final AppContext ctx;
    private final Profile user;
    private final IncidentService service;
    private final boolean isAdmin;
    private final boolean isGuest;

    // KPI Stat Cards
    private final StatCard totalIncidentsCard = new StatCard("Total Incidents", "\u2026", GermsColors.INFO);
    private final StatCard activeIncidentsCard = new StatCard("Active Operations", "\u2026", GermsColors.CRITICAL);
    private final StatCard criticalIncidentsCard = new StatCard("Critical Severity", "\u2026", GermsColors.WARNING);
    private final StatCard deployedRespondersCard = new StatCard("Deployed Responders", "\u2026", GermsColors.SUCCESS);

    // Live Map
    private final OpenStreetMapPanel map = new OpenStreetMapPanel();

    // Incident Pinpoint Table
    private final DefaultTableModel incidentModel = new DefaultTableModel(
            new String[]{"No.", "Title", "Severity", "Status", "Location", "Coordinates"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable incidentTable = new JTable(incidentModel);

    // Deployed Responders Table
    private final DefaultTableModel responderModel = new DefaultTableModel(
            new String[]{"Responder Name", "Role / Task", "Incident", "Deployment Status", "Last Location"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable responderTable = new JTable(responderModel);

    // Broadcast Announcements Table
    private final DefaultTableModel announcementModel = new DefaultTableModel(
            new String[]{"Priority", "Advisory Title", "Broadcast Summary", "Published Date"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable announcementTable = new JTable(announcementModel);

    private final JLabel statusLabel = new JLabel("Synchronizing operational command data...");
    private Runnable busUnsubscribe;
    private final Timer autoRefreshTimer = new Timer(15000, e -> refreshAllData());

    public DashboardPanel(AppContext ctx, Profile user) {
        super(new BorderLayout(0, 10));
        this.ctx = ctx;
        this.user = user;
        this.service = ctx.incidents;
        this.isAdmin = user.role() == Role.ADMIN;
        this.isGuest = user.role() == Role.GUEST;

        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenterContent(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        autoRefreshTimer.setCoalesce(true);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Command Operations Dashboard");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel subtitle = new JLabel(isAdmin
                ? "Incident Command, Live Spatial Monitoring, Deployed Personnel, and Disaster Advisories."
                : "Operational status overview, active field assignments, and verified safety advisories.");
        subtitle.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel titles = new JPanel(new GridLayout(0, 1, 0, 2));
        titles.setOpaque(false);
        titles.add(title);
        titles.add(subtitle);
        header.add(titles, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        if (isAdmin) {
            JButton broadcastBtn = new JButton("+ Broadcast Announcement");
            broadcastBtn.setBackground(GermsColors.PRIMARY_RED);
            broadcastBtn.setForeground(Color.WHITE);
            broadcastBtn.setFont(broadcastBtn.getFont().deriveFont(Font.BOLD));
            broadcastBtn.addActionListener(e -> showBroadcastDialog());
            actions.add(broadcastBtn);
        }

        JButton refreshBtn = new JButton("Refresh Dashboard");
        refreshBtn.addActionListener(e -> refreshAllData());
        actions.add(refreshBtn);

        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JPanel buildCenterContent() {
        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);

        // 1. Top Stat Cards Row
        JPanel kpiRow = new JPanel(new GridLayout(1, 4, 14, 0));
        kpiRow.setOpaque(false);
        kpiRow.setPreferredSize(new Dimension(Integer.MAX_VALUE, 90));
        kpiRow.add(totalIncidentsCard);
        kpiRow.add(activeIncidentsCard);
        kpiRow.add(criticalIncidentsCard);
        kpiRow.add(deployedRespondersCard);
        center.add(kpiRow, BorderLayout.NORTH);

        // 2. Middle Split: Left Map (Live Spatial Pins) and Right Tabbed Operational Feeds
        JPanel mapContainer = new JPanel(new BorderLayout(0, 6));
        mapContainer.setOpaque(false);
        mapContainer.setBorder(BorderFactory.createTitledBorder("Live Operational Map (Ongoing Incidents & Responders)"));

        JPanel mapControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        mapControls.setOpaque(false);
        JButton zoomIn = new JButton("+");
        zoomIn.setMargin(new Insets(2, 8, 2, 8));
        zoomIn.addActionListener(e -> map.zoomIn());
        JButton zoomOut = new JButton("-");
        zoomOut.setMargin(new Insets(2, 8, 2, 8));
        zoomOut.addActionListener(e -> map.zoomOut());
        JButton resetMap = new JButton("Reset Center");
        resetMap.addActionListener(e -> map.resetView());
        JLabel mapHint = new JLabel("Click any incident in the table to focus pin on map.");
        mapHint.setForeground(GermsColors.SECONDARY_TEXT);
        mapHint.setFont(mapHint.getFont().deriveFont(Font.PLAIN, 11f));

        mapControls.add(zoomIn);
        mapControls.add(zoomOut);
        mapControls.add(resetMap);
        mapControls.add(Box.createHorizontalStrut(10));
        mapControls.add(mapHint);

        mapContainer.add(mapControls, BorderLayout.NORTH);
        mapContainer.add(map, BorderLayout.CENTER);

        // Right Operational Tabs
        JTabbedPane rightTabs = new JTabbedPane();
        rightTabs.setFont(rightTabs.getFont().deriveFont(Font.BOLD, 12f));

        // Ongoing Incidents Table
        incidentTable.setRowHeight(26);
        incidentTable.setAutoCreateRowSorter(true);
        DefaultTableCellRenderer sevRenderer = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, val, sel, foc, r, c);
                if (!sel && val != null) {
                    setForeground(GermsColors.forSeverity(val.toString().toLowerCase()));
                    setFont(getFont().deriveFont(Font.BOLD));
                }
                return this;
            }
        };
        DefaultTableCellRenderer statusRenderer = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, val, sel, foc, r, c);
                if (!sel && val != null) {
                    setForeground(GermsColors.forIncidentStatus(val.toString().toLowerCase()));
                    setFont(getFont().deriveFont(Font.BOLD));
                }
                return this;
            }
        };
        incidentTable.getColumnModel().getColumn(2).setCellRenderer(sevRenderer);
        incidentTable.getColumnModel().getColumn(3).setCellRenderer(statusRenderer);

        incidentTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = incidentTable.getSelectedRow();
                if (row >= 0) {
                    String coordStr = String.valueOf(incidentModel.getValueAt(incidentTable.convertRowIndexToModel(row), 5));
                    if (coordStr.contains(",")) {
                        try {
                            String[] parts = coordStr.split(",");
                            double lat = Double.parseDouble(parts[0].trim());
                            double lon = Double.parseDouble(parts[1].trim());
                            map.setCenter(lat, lon, 15);
                        } catch (Exception ignored) {}
                    }
                }
            }
        });
        rightTabs.addTab("Ongoing Incidents (" + incidentModel.getRowCount() + ")", new JScrollPane(incidentTable));

        // Deployed Responders Table
        responderTable.setRowHeight(26);
        responderTable.setAutoCreateRowSorter(true);
        responderTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = responderTable.getSelectedRow();
                if (row >= 0) {
                    String loc = String.valueOf(responderModel.getValueAt(responderTable.convertRowIndexToModel(row), 4));
                    if (loc.contains("(") && loc.contains(")")) {
                        try {
                            String coords = loc.substring(loc.indexOf('(') + 1, loc.indexOf(')'));
                            String[] parts = coords.split(",");
                            double lat = Double.parseDouble(parts[0].trim());
                            double lon = Double.parseDouble(parts[1].trim());
                            map.setCenter(lat, lon, 16);
                        } catch (Exception ignored) {}
                    }
                }
            }
        });
        rightTabs.addTab("Deployed Responders", new JScrollPane(responderTable));

        // Announcements Tab
        announcementTable.setRowHeight(26);
        announcementTable.setAutoCreateRowSorter(true);
        announcementTable.getColumnModel().getColumn(0).setCellRenderer(sevRenderer);
        JPanel annTab = new JPanel(new BorderLayout(0, 6));
        annTab.setOpaque(false);
        annTab.add(new JScrollPane(announcementTable), BorderLayout.CENTER);
        if (isAdmin) {
            JButton quickAnnounce = new JButton("+ Broadcast Public Early Warning / Advisory");
            quickAnnounce.setBackground(GermsColors.PRIMARY_RED);
            quickAnnounce.setForeground(Color.WHITE);
            quickAnnounce.addActionListener(e -> showBroadcastDialog());
            JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            p.setOpaque(false);
            p.add(quickAnnounce);
            annTab.add(p, BorderLayout.SOUTH);
        }
        rightTabs.addTab("Emergency Advisories & Announcements", annTab);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mapContainer, rightTabs);
        split.setResizeWeight(0.52);
        split.setBorder(null);

        center.add(split, BorderLayout.CENTER);
        return center;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        statusLabel.setForeground(GermsColors.SECONDARY_TEXT);
        footer.add(statusLabel, BorderLayout.WEST);
        return footer;
    }

    @Override public void addNotify() {
        super.addNotify();
        if (busUnsubscribe == null) busUnsubscribe = service.bus().subscribe(e -> refreshAllData());
        if (!autoRefreshTimer.isRunning()) autoRefreshTimer.start();
        refreshAllData();
    }

    @Override public void removeNotify() {
        autoRefreshTimer.stop();
        if (busUnsubscribe != null) {
            busUnsubscribe.run();
            busUnsubscribe = null;
        }
        super.removeNotify();
    }

    public void refreshAllData() {
        statusLabel.setText("Querying live command operations...");

        BackgroundTask.run(() -> {
            // 1. Incidents
            List<Incident> incidents = service.visibleTo(isGuest);

            // 2. Active responder deployments
            JsonNode assignments = ctx.rest.select("incident_assignments",
                    "select=id,role_name,status,assigned_at,responder_id,profiles!incident_assignments_responder_id_fkey(full_name),incidents(incident_no,title,location_text)&status=not.in.(completed,released,cancelled)&order=assigned_at.desc");

            // 3. Responder live broadcast locations
            JsonNode respLocations = ctx.rest.select("responder_locations",
                    "select=profile_id,latitude,longitude,recorded_at,source,profiles!responder_locations_profile_id_fkey(full_name)&sharing_enabled=eq.true");

            // 4. Emergency Announcements
            JsonNode announcements = ctx.rest.select("emergency_announcements",
                    "select=*&order=published_at.desc&limit=25");

            return new Object[]{incidents, assignments, respLocations, announcements};
        }, result -> {
            @SuppressWarnings("unchecked")
            List<Incident> incidents = (List<Incident>) result[0];
            JsonNode assignments = (JsonNode) result[1];
            JsonNode respLocations = (JsonNode) result[2];
            JsonNode announcements = (JsonNode) result[3];

            // --- Update KPI Cards ---
            long total = incidents.size();
            long active = incidents.stream().filter(i -> i.status().isActive()).count();
            long critical = incidents.stream().filter(i -> i.status().isActive() && "critical".equalsIgnoreCase(i.severity())).count();
            int deployedCount = (assignments != null && assignments.isArray()) ? assignments.size() : 0;

            totalIncidentsCard.setValue(String.valueOf(total));
            activeIncidentsCard.setValue(String.valueOf(active));
            criticalIncidentsCard.setValue(String.valueOf(critical));
            deployedRespondersCard.setValue(String.valueOf(deployedCount));

            // --- Update Map Markers ---
            List<MapMarker> markers = new ArrayList<>();

            // 1. Incident Pins
            incidentModel.setRowCount(0);
            for (Incident inc : incidents) {
                if (inc.status().isActive() || inc.status() == IncidentStatus.DRAFT) {
                    String coords = "\u2014";
                    if (inc.latitude() != null && inc.longitude() != null) {
                        coords = String.format(java.util.Locale.ROOT, "%.4f, %.4f", inc.latitude(), inc.longitude());
                        markers.add(new MapMarker(
                                inc.id(),
                                inc.reference() + " \u00B7 " + inc.title(),
                                inc.latitude(),
                                inc.longitude(),
                                MarkerType.INCIDENT,
                                inc.severity().toUpperCase() + " | " + inc.status().label() + " | " + inc.location()
                        ));
                    }
                    incidentModel.addRow(new Object[]{
                            inc.reference(),
                            inc.title(),
                            inc.severity().toUpperCase(),
                            inc.status().label(),
                            inc.location(),
                            coords
                    });
                }
            }

            // 2. Responder Location Pins & Table
            responderModel.setRowCount(0);
            if (assignments != null && assignments.isArray()) {
                for (JsonNode a : assignments) {
                    JsonNode prof = a.path("profiles");
                    String name = prof.hasNonNull("full_name") ? prof.path("full_name").asText() : "Field Responder";
                    String role = a.path("role_name").asText("Operational Squad");
                    String status = a.path("status").asText("assigned").toUpperCase();

                    JsonNode inc = a.path("incidents");
                    String incText = inc.hasNonNull("title") ? (inc.path("incident_no").asText() + " " + inc.path("title").asText()) : "Assigned Mission";

                    String locationStr = inc.hasNonNull("location_text") ? inc.path("location_text").asText() : "Mission Staging Area";

                    responderModel.addRow(new Object[]{name, role, incText, status, locationStr});
                }
            }

            // Add live opt-in responders to map
            if (respLocations != null && respLocations.isArray()) {
                for (JsonNode r : respLocations) {
                    double lat = r.path("latitude").asDouble();
                    double lon = r.path("longitude").asDouble();
                    if (lat != 0.0 && lon != 0.0) {
                        JsonNode prof = r.path("profiles");
                        String name = prof.hasNonNull("full_name") ? prof.path("full_name").asText() : "Responder";
                        markers.add(new MapMarker(
                                r.path("profile_id").asText(),
                                "\u25CF " + name,
                                lat,
                                lon,
                                MarkerType.RESPONDER,
                                "Active Unit | Last Signal: " + r.path("recorded_at").asText()
                        ));
                    }
                }
            }

            map.setMarkers(markers);

            // 3. Announcements Feed
            announcementModel.setRowCount(0);
            if (announcements != null && announcements.isArray()) {
                for (JsonNode an : announcements) {
                    String date = an.path("published_at").asText();
                    try {
                        date = TIME_FMT.format(Instant.parse(date));
                    } catch (Exception ignored) {}

                    announcementModel.addRow(new Object[]{
                            an.path("priority").asText("normal").toUpperCase(),
                            an.path("title").asText(),
                            an.path("description").asText(),
                            date
                    });
                }
            }

            statusLabel.setText("System status nominal. Last synchronized at " + TIME_FMT.format(Instant.now())
                    + " \u00B7 " + active + " active incidents \u00B7 " + deployedCount + " personnel deployed.");
        }, err -> {
            statusLabel.setText("Data synchronization error: " + err.getMessage());
        });
    }

    private void showBroadcastDialog() {
        JTextField titleField = new JTextField();
        JTextArea descArea = new JTextArea(4, 24);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        JComboBox<String> priorityBox = new JComboBox<>(new String[]{"normal", "high", "critical"});

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Advisory Title:")); form.add(titleField);
        form.add(new JLabel("Priority Level:")); form.add(priorityBox);
        form.add(new JLabel("Broadcast Message / Instructions:")); form.add(new JScrollPane(descArea));

        int res = JOptionPane.showConfirmDialog(this, form, "Broadcast Incident Command Emergency Announcement", JOptionPane.OK_CANCEL_OPTION);
        if (res != JOptionPane.OK_OPTION) return;

        String title = titleField.getText().trim();
        String desc = descArea.getText().trim();
        if (title.isEmpty() || desc.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Title and description are required.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        BackgroundTask.run(() -> {
            ctx.rest.insertMinimal("emergency_announcements", Map.of(
                    "title", title,
                    "description", desc,
                    "priority", priorityBox.getSelectedItem(),
                    "is_public", true,
                    "status", "published",
                    "created_by", user.id().toString()
            ));
            return Boolean.TRUE;
        }, ok -> {
            refreshAllData();
            JOptionPane.showMessageDialog(this, "Emergency advisory broadcasted to all units and public channels.", "Broadcast Sent", JOptionPane.INFORMATION_MESSAGE);
        }, err -> JOptionPane.showMessageDialog(this, "Broadcast error: " + err.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
    }
}
