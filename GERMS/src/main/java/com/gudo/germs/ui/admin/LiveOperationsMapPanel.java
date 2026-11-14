package com.gudo.germs.ui.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.ui.components.OpenStreetMapPanel;
import com.gudo.germs.ui.components.OpenStreetMapPanel.MapMarker;
import com.gudo.germs.ui.components.OpenStreetMapPanel.MarkerType;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralized Live Operations Map for Incident Command & Responders.
 * Displays interactive OpenStreetMap with live incident coordinates,
 * opt-in deployed responder tracking, evacuation shelters, and resource locations.
 */
public final class LiveOperationsMapPanel extends JPanel {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MMM dd HH:mm").withZone(ZoneId.systemDefault());

    private final AppContext ctx;
    private final Profile user;
    private final OpenStreetMapPanel map = new OpenStreetMapPanel();

    private final JCheckBox showIncidents = new JCheckBox("Incidents", true);
    private final JCheckBox showResponders = new JCheckBox("Responders", true);
    private final JCheckBox showEvacuation = new JCheckBox("Evacuation Centers", true);
    private final JCheckBox showResources = new JCheckBox("Resources", true);

    private final DefaultTableModel listModel = new DefaultTableModel(
            new String[]{"Type", "Title / Name", "Location / Details", "Lat", "Lon"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable listTable = new JTable(listModel);
    private final JLabel statusLabel = new JLabel("Loading operational coordinates...");

    private final List<MapMarker> allMarkers = new ArrayList<>();

    public LiveOperationsMapPanel(AppContext ctx, Profile user) {
        super(new BorderLayout(0, 10));
        this.ctx = ctx;
        this.user = user;
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildMainSplit(), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        loadData();
    }

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout(12, 4));
        p.setOpaque(false);

        JLabel title = new JLabel("Live Operations Map");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel subtitle = new JLabel("Centralized GIS tracking: Active incidents, responder positions, evacuation shelters, and staging areas.");
        subtitle.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel titles = new JPanel(new GridLayout(0, 1, 0, 2));
        titles.setOpaque(false);
        titles.add(title);
        titles.add(subtitle);
        p.add(titles, BorderLayout.WEST);

        // Quick Controls
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);

        controls.add(showIncidents);
        controls.add(showResponders);
        controls.add(showEvacuation);
        controls.add(showResources);

        ActionListener filterAction = e -> applyFilters();
        showIncidents.addActionListener(filterAction);
        showResponders.addActionListener(filterAction);
        showEvacuation.addActionListener(filterAction);
        showResources.addActionListener(filterAction);

        JButton btnDavao = new JButton("Davao Center");
        btnDavao.addActionListener(e -> map.setCenter(7.0731, 125.6128, 13));
        JButton btnSPMC = new JButton("SPMC Hospital");
        btnSPMC.addActionListener(e -> map.setCenter(7.0988, 125.6267, 15));
        JButton btnRedCross = new JButton("Red Cross Chapter");
        btnRedCross.addActionListener(e -> map.setCenter(7.0682, 125.6067, 16));

        JButton btnZoomIn = new JButton("+");
        btnZoomIn.addActionListener(e -> map.zoomIn());
        JButton btnZoomOut = new JButton("\u2212");
        btnZoomOut.addActionListener(e -> map.zoomOut());

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadData());

        controls.add(btnDavao);
        controls.add(btnSPMC);
        controls.add(btnRedCross);
        controls.add(btnZoomIn);
        controls.add(btnZoomOut);
        controls.add(refresh);

        p.add(controls, BorderLayout.EAST);
        return p;
    }

    private JComponent buildMainSplit() {
        listTable.setRowHeight(24);
        listTable.setAutoCreateRowSorter(true);
        listTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int r = listTable.getSelectedRow();
                if (r >= 0) {
                    int modelRow = listTable.convertRowIndexToModel(r);
                    try {
                        double lat = Double.parseDouble(String.valueOf(listModel.getValueAt(modelRow, 3)));
                        double lon = Double.parseDouble(String.valueOf(listModel.getValueAt(modelRow, 4)));
                        map.setCenter(lat, lon, 15);
                    } catch (Exception ignored) {}
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(listTable);
        tableScroll.setPreferredSize(new Dimension(340, 400));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, map, tableScroll);
        split.setResizeWeight(0.72);
        split.setBorder(BorderFactory.createLineBorder(new Color(210, 214, 220)));
        return split;
    }

    public void loadData() {
        statusLabel.setText("Querying live operational coordinates...");
        BackgroundTask.run(() -> {
            // 1. Incidents
            JsonNode incRows = ctx.rest.select("incidents",
                    "select=id,incident_no,title,category,severity,status,location_text,latitude,longitude,updated_at&latitude=not.is.null&order=updated_at.desc");

            // 2. Responders with location sharing enabled
            JsonNode respRows = ctx.rest.select("responder_locations",
                    "select=profile_id,latitude,longitude,accuracy_m,source,sharing_enabled,recorded_at,profiles!responder_locations_profile_id_fkey(full_name)&sharing_enabled=eq.true");

            // 3. Evacuation Centers
            JsonNode evacRows = ctx.rest.select("evacuation_centers",
                    "select=id,name,address,capacity,occupancy,status,latitude,longitude&latitude=not.is.null");

            // 4. Resources
            JsonNode resRows = ctx.rest.select("resources",
                    "select=id,name,category,status,location,quantity,available_quantity&order=name.asc");

            return new Object[]{incRows, respRows, evacRows, resRows};
        }, result -> {
            allMarkers.clear();
            listModel.setRowCount(0);

            JsonNode incRows = (JsonNode) result[0];
            JsonNode respRows = (JsonNode) result[1];
            JsonNode evacRows = (JsonNode) result[2];
            JsonNode resRows = (JsonNode) result[3];

            int incCount = 0;
            if (incRows != null && incRows.isArray()) {
                for (JsonNode i : incRows) {
                    if (i.hasNonNull("latitude") && i.hasNonNull("longitude")) {
                        double lat = i.path("latitude").asDouble();
                        double lon = i.path("longitude").asDouble();
                        String title = i.path("incident_no").asText() + " \u00B7 " + i.path("title").asText();
                        String details = i.path("severity").asText().toUpperCase() + " | " + i.path("status").asText() + " | " + i.path("location_text").asText();
                        allMarkers.add(new MapMarker(i.path("id").asText(), title, lat, lon, MarkerType.INCIDENT, details));
                        incCount++;
                    }
                }
            }

            int respCount = 0;
            if (respRows != null && respRows.isArray()) {
                for (JsonNode r : respRows) {
                    double lat = r.path("latitude").asDouble();
                    double lon = r.path("longitude").asDouble();
                    JsonNode prof = r.path("profiles");
                    String name = prof.hasNonNull("full_name") ? prof.path("full_name").asText() : "Responder";
                    String timeStr = r.path("recorded_at").asText();
                    String details = "Reported: " + (timeStr.length() > 16 ? timeStr.substring(0, 16).replace('T', ' ') : timeStr) + " (Source: " + r.path("source").asText() + ")";
                    allMarkers.add(new MapMarker(r.path("profile_id").asText(), "\u25CF " + name, lat, lon, MarkerType.RESPONDER, details));
                    respCount++;
                }
            }

            int evacCount = 0;
            if (evacRows != null && evacRows.isArray()) {
                for (JsonNode e : evacRows) {
                    if (e.hasNonNull("latitude") && e.hasNonNull("longitude")) {
                        double lat = e.path("latitude").asDouble();
                        double lon = e.path("longitude").asDouble();
                        String name = e.path("name").asText();
                        int cap = e.path("capacity").asInt();
                        int occ = e.path("occupancy").asInt();
                        String details = "Status: " + e.path("status").asText().toUpperCase() + " | Occupancy: " + occ + " / " + cap;
                        allMarkers.add(new MapMarker(e.path("id").asText(), "\u2302 " + name, lat, lon, MarkerType.EVACUATION, details));
                        evacCount++;
                    }
                }
            }

            applyFilters();
            statusLabel.setText(String.format("Loaded: %d incidents, %d live responders, %d evacuation shelters.", incCount, respCount, evacCount));
        }, err -> {
            statusLabel.setText("Failed to load map points: " + err.getMessage());
        });
    }

    private void applyFilters() {
        boolean inc = showIncidents.isSelected();
        boolean resp = showResponders.isSelected();
        boolean evac = showEvacuation.isSelected();
        boolean res = showResources.isSelected();

        List<MapMarker> filtered = new ArrayList<>();
        listModel.setRowCount(0);

        for (MapMarker m : allMarkers) {
            boolean keep = false;
            if (m.type() == MarkerType.INCIDENT && inc) keep = true;
            else if (m.type() == MarkerType.RESPONDER && resp) keep = true;
            else if (m.type() == MarkerType.EVACUATION && evac) keep = true;
            else if (m.type() == MarkerType.RESOURCE && res) keep = true;

            if (keep) {
                filtered.add(m);
                listModel.addRow(new Object[]{
                        m.type().defaultLabel,
                        m.title(),
                        m.details(),
                        String.format(java.util.Locale.ROOT, "%.5f", m.lat()),
                        String.format(java.util.Locale.ROOT, "%.5f", m.lon())
                });
            }
        }
        map.setMarkers(filtered);
    }
}
