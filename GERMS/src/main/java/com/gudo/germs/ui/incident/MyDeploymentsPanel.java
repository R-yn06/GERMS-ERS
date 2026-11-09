package com.gudo.germs.ui.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.ui.components.LocationPickerDialog;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * My Deployments & Live Location Tracking Interface.
 * Allows responders to monitor assigned missions and explicitly share a selected operational location.
 */
public final class MyDeploymentsPanel extends JPanel {
    private final AppContext ctx;
    private final Profile user;

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Assignment ID", "Incident", "Role", "Status", "Destination", "Assigned At"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);

    private final JCheckBox locationShareToggle = new JCheckBox(
            "Opt-in: Share My Selected Location (not continuous GPS tracking)", false);
    private final JButton updateLocationBtn = new JButton("Select / Move Location on Map");
    private LocationPickerDialog.Selection selectedLocation;

    private final JButton stepNextBtn = new JButton("Advance Deployment Status");
    private final JLabel trackingStatus = new JLabel("Location Sharing: Disabled");
    private final JLabel statusMessage = new JLabel(" ");

    public MyDeploymentsPanel(AppContext ctx, Profile user) {
        super(new BorderLayout(0, 12));
        this.ctx = ctx;
        this.user = user;
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        add(buildHeader(), BorderLayout.NORTH);

        table.setRowHeight(26);
        table.removeColumn(table.getColumnModel().getColumn(0)); // Hide ID
        table.setAutoCreateRowSorter(true);
        add(new JScrollPane(table), BorderLayout.CENTER);

        add(buildBottomControls(), BorderLayout.SOUTH);

        locationHeartbeat.setCoalesce(true);
        loadDeployments();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("My Deployments & Mission Command");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel desc = new JLabel("Review deployments and choose a map pin or approximate network location to share with command.");
        desc.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel p = new JPanel(new GridLayout(0, 1, 0, 2));
        p.setOpaque(false);
        p.add(title);
        p.add(desc);
        header.add(p, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        stepNextBtn.setBackground(GermsColors.PRIMARY_RED);
        stepNextBtn.setForeground(Color.WHITE);
        stepNextBtn.addActionListener(e -> advanceSelectedAssignment());

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadDeployments());

        actions.add(stepNextBtn);
        actions.add(refresh);

        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JPanel buildBottomControls() {
        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);

        // Location Tracking Box
        JPanel trackingBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        trackingBox.setBorder(BorderFactory.createTitledBorder("Operational Location Sharing (Opt-In)"));

        locationShareToggle.addActionListener(e -> {
            boolean sharing = locationShareToggle.isSelected();
            if (sharing && selectedLocation == null) {
                if (!chooseLocation()) {
                    locationShareToggle.setSelected(false);
                    return;
                }
            }
            trackingStatus.setText(sharing ? "Location Sharing: Active (selected location)" : "Location Sharing: Disabled");
            trackingStatus.setForeground(sharing ? GermsColors.SUCCESS : GermsColors.SECONDARY_TEXT);
            if (sharing) {
                transmitSelectedLocation();
            } else {
                disableLocationSharing();
            }
        });

        trackingBox.add(locationShareToggle);
        updateLocationBtn.addActionListener(e -> {
            if (chooseLocation() && locationShareToggle.isSelected()) transmitSelectedLocation();
        });
        trackingBox.add(updateLocationBtn);
        trackingBox.add(trackingStatus);

        bottom.add(trackingBox, BorderLayout.NORTH);
        bottom.add(statusMessage, BorderLayout.SOUTH);

        return bottom;
    }

    @Override public void removeNotify() {
        if (locationShareToggle.isSelected()) {
            locationShareToggle.setSelected(false);
            disableLocationSharing();
        }
        super.removeNotify();
    }

    public void loadDeployments() {
        statusMessage.setText("Loading assignments...");
        BackgroundTask.run(() -> {
            return ctx.rest.select("incident_assignments",
                    "select=id,role_name,status,assigned_at,incidents(incident_no,title,location_text)&responder_id=eq." + user.id() + "&order=assigned_at.desc");
        }, rows -> {
            model.setRowCount(0);
            if (rows != null && rows.isArray()) {
                for (JsonNode a : rows) {
                    JsonNode inc = a.path("incidents");
                    String incTitle = inc.hasNonNull("title") ? (inc.path("incident_no").asText() + " " + inc.path("title").asText()) : "Incident";
                    String dest = inc.hasNonNull("location_text") ? inc.path("location_text").asText() : "Command Staging Area";

                    String date = a.path("assigned_at").asText();
                    if (date.length() > 19) date = date.substring(0, 19).replace('T', ' ');

                    model.addRow(new Object[]{
                            a.path("id").asText(),
                            incTitle,
                            a.path("role_name").asText("Assigned Responder"),
                            a.path("status").asText("assigned").toUpperCase(),
                            dest,
                            date
                    });
                }
            }
            statusMessage.setText(model.getRowCount() + " deployment record(s) found.");
            if (table.getRowCount() > 0 && table.getSelectedRow() < 0) {
                table.setRowSelectionInterval(0, 0);
            }
        }, err -> statusMessage.setText("Could not load deployments: " + err.getMessage()));
    }

    private void advanceSelectedAssignment() {
        int r = table.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Please select an assignment to advance.", "Notice", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = table.convertRowIndexToModel(r);
        String assignmentId = String.valueOf(model.getValueAt(modelRow, 0));
        String currentStatus = String.valueOf(model.getValueAt(modelRow, 3)).toLowerCase();

        String nextStatus = switch (currentStatus) {
            case "assigned" -> "preparing";
            case "preparing" -> "en_route";
            case "en_route" -> "arrived";
            case "arrived" -> "on_task";
            case "on_task" -> "completed";
            default -> null;
        };

        if (nextStatus == null) {
            JOptionPane.showMessageDialog(this, "Assignment is already in final status (" + currentStatus.toUpperCase() + ").", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Advance deployment status to: " + nextStatus.toUpperCase() + "?",
                "Advance Mission Status", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        BackgroundTask.run(() -> {
            ctx.rest.rpc("update_assignment_status", Map.of(
                    "p_assignment", UUID.fromString(assignmentId),
                    "p_to", nextStatus,
                    "p_note", "Status advanced by responder: " + user.fullName()
            ));
            return Boolean.TRUE;
        }, ok -> {
            loadDeployments();
            JOptionPane.showMessageDialog(this, "Mission status updated to " + nextStatus.toUpperCase() + ".", "Status Updated", JOptionPane.INFORMATION_MESSAGE);
        }, err -> JOptionPane.showMessageDialog(this, "Update failed: " + err.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
    }

    private void transmitSelectedLocation() {
        if (!locationShareToggle.isSelected()) return;
        if (selectedLocation == null) {
            trackingStatus.setText("Select a location on the map before sharing.");
            trackingStatus.setForeground(GermsColors.CRITICAL);
            return;
        }

        LocationPickerDialog.Selection location = selectedLocation;
        Map<String, Object> args = new HashMap<>();
        args.put("p_lat", location.latitude());
        args.put("p_lon", location.longitude());
        args.put("p_accuracy", location.accuracyMeters());
        args.put("p_source", location.source());
        args.put("p_sharing", true);
        BackgroundTask.run(() -> {
            ctx.rest.rpc("update_responder_location", args);
            return Boolean.TRUE;
        }, ok -> {
            trackingStatus.setText(String.format("%s shared (%.5f, %.5f)",
                    location.label(), location.latitude(), location.longitude()));
            trackingStatus.setForeground(GermsColors.SUCCESS);
        }, err -> {
            trackingStatus.setText("Location Sync Error: " + err.getMessage());
            trackingStatus.setForeground(GermsColors.CRITICAL);
        });
    }

    private boolean chooseLocation() {
        LocationPickerDialog.Selection picked = LocationPickerDialog.showDialog(
                SwingUtilities.getWindowAncestor(this), "Choose Operational Location", selectedLocation);
        if (picked == null) return false;
        selectedLocation = picked;
        trackingStatus.setText(String.format("Selected: %s (%.5f, %.5f)",
                picked.label(), picked.latitude(), picked.longitude()));
        trackingStatus.setForeground(GermsColors.SECONDARY_TEXT);
        return true;
    }

    private void disableLocationSharing() {
        BackgroundTask.run(() -> {
            ctx.rest.rpc("update_responder_location", Map.of(
                    "p_lat", 0.0,
                    "p_lon", 0.0,
                    "p_accuracy", 0.0,
                    "p_source", "manual",
                    "p_sharing", false
            ));
            return Boolean.TRUE;
        }, ok -> {
            trackingStatus.setText("Location Sharing: Disabled");
            trackingStatus.setForeground(GermsColors.SECONDARY_TEXT);
        }, err -> {
            trackingStatus.setText("Could not disable sharing on server: " + err.getMessage());
            trackingStatus.setForeground(GermsColors.CRITICAL);
        });
    }
}
