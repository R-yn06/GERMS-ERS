package com.gudo.germs.ui.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Map;
import java.util.UUID;

/**
 * My Deployments & Live Location Tracking Interface.
 * Allows responders to monitor their assigned missions, progress assignment status
 * (preparing, en route, arrived, on task, completed), and toggle opt-in live GPS
 * location sharing with the Incident Command center.
 */
public final class MyDeploymentsPanel extends JPanel {
    private final AppContext ctx;
    private final Profile user;

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Assignment ID", "Incident", "Role", "Status", "Destination", "Assigned At"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);

    private final JCheckBox locationShareToggle = new JCheckBox("Opt-in: Share My Operational Location with Command Center", false);
    private final JTextField latField = new JTextField("7.0731", 8);
    private final JTextField lonField = new JTextField("125.6128", 8);
    private final JButton updateLocationBtn = new JButton("Transmit Coordinates Now");

    private final JButton stepNextBtn = new JButton("Advance Deployment Status");
    private final JLabel trackingStatus = new JLabel("Location Sharing: Disabled");
    private final JLabel statusMessage = new JLabel(" ");

    private final Timer locationHeartbeat = new Timer(15000, e -> transmitLocationHeartbeat());

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
        JLabel desc = new JLabel("Review active incident deployments, advance operational response status, and report GPS location.");
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
        trackingBox.setBorder(BorderFactory.createTitledBorder("Live Operational Location Tracking (Opt-In)"));

        locationShareToggle.addActionListener(e -> {
            boolean sharing = locationShareToggle.isSelected();
            trackingStatus.setText(sharing ? "Location Sharing: ACTIVE (Transmitting to Operations Map)" : "Location Sharing: Disabled");
            trackingStatus.setForeground(sharing ? GermsColors.SUCCESS : GermsColors.SECONDARY_TEXT);
            if (sharing) {
                transmitLocationHeartbeat();
                if (!locationHeartbeat.isRunning()) locationHeartbeat.start();
            } else {
                locationHeartbeat.stop();
                disableLocationSharing();
            }
        });

        trackingBox.add(locationShareToggle);
        trackingBox.add(new JLabel("Latitude:"));
        trackingBox.add(latField);
        trackingBox.add(new JLabel("Longitude:"));
        trackingBox.add(lonField);

        updateLocationBtn.addActionListener(e -> transmitLocationHeartbeat());
        trackingBox.add(updateLocationBtn);
        trackingBox.add(trackingStatus);

        bottom.add(trackingBox, BorderLayout.NORTH);
        bottom.add(statusMessage, BorderLayout.SOUTH);

        return bottom;
    }

    @Override public void removeNotify() {
        locationHeartbeat.stop();
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

    private void transmitLocationHeartbeat() {
        if (!locationShareToggle.isSelected()) return;
        try {
            double lat = Double.parseDouble(latField.getText().trim());
            double lon = Double.parseDouble(lonField.getText().trim());

            BackgroundTask.run(() -> {
                ctx.rest.rpc("update_responder_location", Map.of(
                        "p_lat", lat,
                        "p_lon", lon,
                        "p_accuracy", 5.0,
                        "p_source", "device",
                        "p_sharing", true
                ));
                return Boolean.TRUE;
            }, ok -> {
                trackingStatus.setText(String.format("Location Transmitted (%.4f, %.4f)", lat, lon));
                trackingStatus.setForeground(GermsColors.SUCCESS);
            }, err -> {
                trackingStatus.setText("Location Sync Error: " + err.getMessage());
                trackingStatus.setForeground(GermsColors.CRITICAL);
            });
        } catch (Exception e) {
            trackingStatus.setText("Invalid coordinate input.");
        }
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
        }, ok -> {}, err -> {});
    }
}

