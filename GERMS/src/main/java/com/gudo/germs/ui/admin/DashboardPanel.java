package com.gudo.germs.ui.admin;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Incident;
import com.gudo.germs.models.IncidentStatus;
import com.gudo.germs.models.Profile;
import com.gudo.germs.utils.ResponderClassificationCodes;
import com.gudo.germs.models.Role;
import com.gudo.germs.services.IncidentService;
import com.gudo.germs.ui.components.OpenStreetMapPanel;
import com.gudo.germs.ui.components.OpenStreetMapPanel.MapMarker;
import com.gudo.germs.ui.components.OpenStreetMapPanel.MarkerType;
import com.gudo.germs.ui.components.StatCard;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

/**
 * Command Dashboard & Central Operations Hub.
 * Streamlines incident statistics, deployed personnel tracking, live
 * OpenStreetMap pinpoints of
 * ongoing incidents and responders.
 */
public final class DashboardPanel extends JPanel implements com.gudo.germs.ui.RefreshablePanel {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MMM dd, HH:mm")
            .withZone(ZoneId.systemDefault());

    private final AppContext ctx;
    private final IncidentService service;
    private final boolean isGuest;
    private final Profile user;
    private final boolean isResponder;
    private static final ClassificationOption[] RESPONDER_CLASSIFICATIONS = {
            new ClassificationOption("", "Select classification"),
            new ClassificationOption("EMS", "EMS - Emergency Medical Services"),
            new ClassificationOption("ERU", "ERU - Emergency Response Unit"),
            new ClassificationOption("EMR", "EMR - Emergency Medical Responder"),
            new ClassificationOption("EMT", "EMT - Emergency Medical Technician"),
            new ClassificationOption("DMS", "DMS - Disaster Management Services")
    };
    private final JComboBox<ClassificationOption> classificationSelector = new JComboBox<>(
            RESPONDER_CLASSIFICATIONS);
    private final JButton saveClassificationButton = new JButton("Save Classification");
    private final JLabel classificationStatus = new JLabel(" ");
    private boolean classificationLoaded;

    // KPI Stat Cards
    private final StatCard totalIncidentsCard = new StatCard("Total Incidents", "\u2026", GermsColors.INFO);
    private final StatCard activeIncidentsCard = new StatCard("Active Operations", "\u2026", GermsColors.CRITICAL);
    private final StatCard criticalIncidentsCard = new StatCard("Critical Severity", "\u2026", GermsColors.WARNING);
    private final StatCard deployedRespondersCard = new StatCard("Deployed Responders", "\u2026", GermsColors.SUCCESS);

    // Live Map
    private final OpenStreetMapPanel map = new OpenStreetMapPanel();

    // Incident Pinpoint Table
    private final DefaultTableModel incidentModel = new DefaultTableModel(
            new String[] { "No.", "Title", "Severity", "Status", "Location", "Coordinates" }, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable incidentTable = new JTable(incidentModel);

    // Deployed Responders Table
    private final DefaultTableModel responderModel = new DefaultTableModel(
            new String[] { "Responder Name", "Role / Task", "Incident", "Deployment Status", "Last Location" }, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable responderTable = new JTable(responderModel);

    private final JLabel statusLabel = new JLabel("Synchronizing operational command data...");
    private Runnable busUnsubscribe;

    public DashboardPanel(AppContext ctx, Profile user) {
        super(new BorderLayout(0, 10));
        this.ctx = ctx;
        this.service = ctx.incidents;
        this.isGuest = user.role() == Role.GUEST;
        this.user = user;
        this.isResponder = user.role() == Role.RESPONDER;

        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenterContent(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 10));
        header.setOpaque(false);

        // Row 1: title + subtitle
        JLabel title = new JLabel("Command Operations Dashboard");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel subtitle = new JLabel("Incident command, live spatial monitoring, and deployed personnel.");
        subtitle.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel titles = new JPanel(new GridLayout(0, 1, 0, 2));
        titles.setOpaque(false);
        title.setText(isResponder ? "Responder Dashboard" : "Command Operations Dashboard");
        titles.add(title);
        titles.add(subtitle);
        header.add(titles, BorderLayout.NORTH);

        // Row 2: action buttons
        // BoxLayout keeps everything in one row at its natural size (no wrapping).
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));

        JButton refreshBtn = new JButton("Refresh Dashboard");
        refreshBtn.addActionListener(e -> refreshAllData());
        actions.add(refreshBtn);
        actions.add(Box.createHorizontalGlue()); // pushes buttons to the left

        header.add(actions, BorderLayout.CENTER);
        if (isResponder) {
            header.add(buildClassificationControls(), BorderLayout.SOUTH);
        }
        return header;
    }

    private JPanel buildClassificationControls() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        controls.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        controls.add(new JLabel("My responder classification:"));
        classificationSelector.setEnabled(false);
        classificationSelector.addActionListener(e -> updateClassificationSaveState());
        controls.add(classificationSelector);

        saveClassificationButton.setEnabled(false);
        saveClassificationButton.addActionListener(e -> saveResponderClassification());
        controls.add(saveClassificationButton);

        JButton reload = new JButton("Load");
        reload.setToolTipText("Load your current responder classification");
        reload.addActionListener(e -> loadResponderClassification());
        controls.add(reload);
        controls.add(classificationStatus);
        loadResponderClassification();
        return controls;
    }

    private void loadResponderClassification() {
        classificationLoaded = false;
        classificationSelector.setEnabled(false);
        saveClassificationButton.setEnabled(false);
        classificationStatus.setForeground(GermsColors.SECONDARY_TEXT);
        classificationStatus.setText("Loading...");
        BackgroundTask.run(() -> ctx.rest.select("profiles",
                "select=profile_classifications!profile_classifications_profile_id_fkey(responder_classifications(code))"
                        + "&id=eq." + user.id()), rows -> {
            ClassificationOption current = null;
            if (rows != null && rows.isArray() && !rows.isEmpty()) {
                JsonNode assignments = rows.get(0).path("profile_classifications");
                if (assignments.isArray() && !assignments.isEmpty()) {
                    String code = assignments.get(0).path("responder_classifications").path("code").asText();
                    current = classificationFor(code);
                }
            }
            classificationLoaded = true;
            classificationSelector.setEnabled(true);
            classificationSelector.setSelectedItem(current == null
                    ? classificationSelector.getItemAt(0)
                    : current);
            updateClassificationSaveState();
            classificationStatus.setText(current == null
                    ? "Choose one option and save."
                    : "Current classification loaded.");
        }, error -> {
            classificationLoaded = false;
            classificationStatus.setForeground(GermsColors.CRITICAL);
            classificationStatus.setText("Could not load: " + error.getMessage());
        });
    }

    private ClassificationOption classificationFor(String code) {
        String normalized = ResponderClassificationCodes.canonicalCode(code);
        for (ClassificationOption option : RESPONDER_CLASSIFICATIONS) {
            if (option.code().equals(normalized)) {
                return option;
            }
        }
        return null;
    }

    private void updateClassificationSaveState() {
        ClassificationOption selected = (ClassificationOption) classificationSelector.getSelectedItem();
        saveClassificationButton.setEnabled(classificationLoaded && selected != null && !selected.code().isBlank());
    }

    private void saveResponderClassification() {
        ClassificationOption selected = (ClassificationOption) classificationSelector.getSelectedItem();
        if (!classificationLoaded || selected == null || selected.code().isBlank()) return;

        classificationLoaded = false;
        classificationSelector.setEnabled(false);
        saveClassificationButton.setEnabled(false);
        classificationStatus.setForeground(GermsColors.SECONDARY_TEXT);
        classificationStatus.setText("Saving...");
        BackgroundTask.run(() -> {
            ctx.rest.rpc("set_own_responder_classification", Map.of(
                    "p_classification_code", selected.code()));
            return Boolean.TRUE;
        }, ok -> {
            classificationLoaded = true;
            classificationSelector.setEnabled(true);
            updateClassificationSaveState();
            classificationStatus.setForeground(GermsColors.SUCCESS);
            classificationStatus.setText("Classification saved.");
        }, error -> {
            classificationLoaded = true;
            classificationSelector.setEnabled(true);
            updateClassificationSaveState();
            classificationStatus.setForeground(GermsColors.CRITICAL);
            classificationStatus.setText("Could not save: " + error.getMessage());
        });
    }

    private record ClassificationOption(String code, String label) {
        @Override
        public String toString() {
            return label;
        }
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

        // 2. Middle Split: Left Map (Live Spatial Pins) and Right Tabbed Operational
        // Feeds
        JPanel mapContainer = new JPanel(new BorderLayout(0, 6));
        mapContainer.setOpaque(false);
        mapContainer
                .setBorder(BorderFactory.createTitledBorder("Live Operational Map (Ongoing Incidents & Responders)"));

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
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int r,
                    int c) {
                super.getTableCellRendererComponent(t, val, sel, foc, r, c);
                if (!sel && val != null) {
                    setForeground(GermsColors.forSeverity(val.toString().toLowerCase()));
                    setFont(getFont().deriveFont(Font.BOLD));
                }
                return this;
            }
        };
        DefaultTableCellRenderer statusRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int r,
                    int c) {
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
                    String coordStr = String
                            .valueOf(incidentModel.getValueAt(incidentTable.convertRowIndexToModel(row), 5));
                    if (coordStr.contains(",")) {
                        try {
                            String[] parts = coordStr.split(",");
                            double lat = Double.parseDouble(parts[0].trim());
                            double lon = Double.parseDouble(parts[1].trim());
                            map.setCenter(lat, lon, 15);
                        } catch (Exception ignored) {
                        }
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
                    String loc = String
                            .valueOf(responderModel.getValueAt(responderTable.convertRowIndexToModel(row), 4));
                    if (loc.contains("(") && loc.contains(")")) {
                        try {
                            String coords = loc.substring(loc.indexOf('(') + 1, loc.indexOf(')'));
                            String[] parts = coords.split(",");
                            double lat = Double.parseDouble(parts[0].trim());
                            double lon = Double.parseDouble(parts[1].trim());
                            map.setCenter(lat, lon, 16);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        });
        rightTabs.addTab("Deployed Responders", new JScrollPane(responderTable));

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

    @Override
    public void addNotify() {
        super.addNotify();
        if (busUnsubscribe == null)
            busUnsubscribe = service.bus().subscribe(e -> refreshAllData());
        refreshAllData();
    }

    @Override
    public void removeNotify() {
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

            return new Object[] { incidents, assignments, respLocations };
        }, result -> {
            @SuppressWarnings("unchecked")
            List<Incident> incidents = (List<Incident>) result[0];
            JsonNode assignments = (JsonNode) result[1];
            JsonNode respLocations = (JsonNode) result[2];

            // --- Update KPI Cards ---
            long total = incidents.size();
            long active = incidents.stream().filter(i -> i.status().isActive()).count();
            long critical = incidents.stream()
                    .filter(i -> i.status().isActive() && "critical".equalsIgnoreCase(i.severity())).count();
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
                                inc.severity().toUpperCase() + " | " + inc.status().label() + " | " + inc.location()));
                    }
                    incidentModel.addRow(new Object[] {
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
                    String incText = inc.hasNonNull("title")
                            ? (inc.path("incident_no").asText() + " " + inc.path("title").asText())
                            : "Assigned Mission";

                    String locationStr = inc.hasNonNull("location_text") ? inc.path("location_text").asText()
                            : "Mission Staging Area";

                    responderModel.addRow(new Object[] { name, role, incText, status, locationStr });
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
                                "Active Unit | Last Signal: " + r.path("recorded_at").asText()));
                    }
                }
            }

            map.setMarkers(markers);

            statusLabel.setText("System status nominal. Last synchronized at " + TIME_FMT.format(Instant.now())
                    + " \u00B7 " + active + " active incidents \u00B7 " + deployedCount + " personnel deployed.");
        }, err -> {
            statusLabel.setText("Data synchronization error: " + err.getMessage());
        });
    }

    @Override
    public void refreshData() {
        refreshAllData();
    }

}
