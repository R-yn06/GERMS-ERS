package com.gudo.germs.ui.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.services.AlarmService;
import com.gudo.germs.ui.components.LocationPickerDialog;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * My Deployments, Mission Command, Notifications & Emergency Alarms.
 * Combines incident assignments, operational location sharing, and real-time audible alarms.
 */
public final class MyDeploymentsPanel extends JPanel {
    private final AppContext ctx;
    private final Profile user;
    private final AlarmService alarms = AlarmService.get();

    // Tabs
    private final JTabbedPane tabbedPane = new JTabbedPane();

    // Deployment Tab Components
    private final DefaultTableModel deploymentModel = new DefaultTableModel(
            new String[]{"Assignment ID", "Incident", "Role", "Status", "Destination", "Assigned At"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable deploymentTable = new JTable(deploymentModel);
    private final JCheckBox locationShareToggle = new JCheckBox(
            "Opt-in: Share My Operational Location (Network / Pin)", false);
    private final JButton updateLocationBtn = new JButton("Select / Move Location on Map");
    private LocationPickerDialog.Selection selectedLocation;
    private final JButton stepNextBtn = new JButton("Advance Deployment Status");
    private final JLabel trackingStatus = new JLabel("Location Sharing: Disabled");
    private final JLabel deploymentStatusMsg = new JLabel(" ");

    // Notifications & Alarm Tab Components
    private final DefaultTableModel notifModel = new DefaultTableModel(
            new String[]{"ID", "Priority", "Title", "Incident / Location", "Timestamp", "Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable notifTable = new JTable(notifModel);
    private final JCheckBox audibleToggle = new JCheckBox("Enable Audible Alarms", true);
    private final JLabel alarmStatus = new JLabel("Alarm: Idle");
    private final JLabel notifStatusMsg = new JLabel(" ");
    private final Timer notifPoll = new Timer(5000, e -> loadNotifications());

    public MyDeploymentsPanel(AppContext ctx, Profile user) {
        super(new BorderLayout(0, 10));
        this.ctx = ctx;
        this.user = user;
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        add(buildHeader(), BorderLayout.NORTH);

        tabbedPane.addTab("Active Deployments & Missions", buildDeploymentsTab());
        tabbedPane.addTab("Notifications & Emergency Alarms", buildNotificationsTab());
        tabbedPane.setFont(tabbedPane.getFont().deriveFont(Font.BOLD, 13f));
        add(tabbedPane, BorderLayout.CENTER);

        notifPoll.setCoalesce(true);
        loadDeployments();
        loadNotifications();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("My Deployments & Mission Center");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel desc = new JLabel("Manage assigned deployments, location broadcast, and monitor emergency alarm dispatch feeds.");
        desc.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel p = new JPanel(new GridLayout(0, 1, 0, 2));
        p.setOpaque(false);
        p.add(title);
        p.add(desc);
        header.add(p, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        JButton silenceQuick = new JButton("Silence Alarm");
        silenceQuick.setBackground(GermsColors.PRIMARY_RED);
        silenceQuick.setForeground(Color.WHITE);
        silenceQuick.setFont(silenceQuick.getFont().deriveFont(Font.BOLD));
        silenceQuick.addActionListener(e -> {
            alarms.stopAlarm();
            alarmStatus.setText("Alarm: Silenced");
            alarmStatus.setForeground(GermsColors.SECONDARY_TEXT);
        });

        JButton refreshAll = new JButton("Refresh All");
        refreshAll.addActionListener(e -> {
            loadDeployments();
            loadNotifications();
        });

        actions.add(alarmStatus);
        actions.add(silenceQuick);
        actions.add(refreshAll);
        header.add(actions, BorderLayout.EAST);

        return header;
    }

    private JPanel buildDeploymentsTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        deploymentTable.setRowHeight(28);
        deploymentTable.removeColumn(deploymentTable.getColumnModel().getColumn(0)); // Hide Assignment ID
        deploymentTable.setAutoCreateRowSorter(true);

        DefaultTableCellRenderer statusRenderer = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, val, sel, foc, r, c);
                if (!sel && val != null) {
                    String st = val.toString().toLowerCase();
                    setForeground(GermsColors.forIncidentStatus(st));
                    setFont(getFont().deriveFont(Font.BOLD));
                }
                return this;
            }
        };
        deploymentTable.getColumnModel().getColumn(2).setCellRenderer(statusRenderer);

        panel.add(new JScrollPane(deploymentTable), BorderLayout.CENTER);

        // Deployment Bottom Controls
        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);

        JPanel trackingBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        trackingBox.setBorder(BorderFactory.createTitledBorder("Operational Location Broadcast (Opt-In)"));

        locationShareToggle.addActionListener(e -> {
            boolean sharing = locationShareToggle.isSelected();
            if (sharing && selectedLocation == null) {
                if (!chooseLocation()) {
                    locationShareToggle.setSelected(false);
                    return;
                }
            }
            trackingStatus.setText(sharing ? "Location Sharing: Active" : "Location Sharing: Disabled");
            trackingStatus.setForeground(sharing ? GermsColors.SUCCESS : GermsColors.SECONDARY_TEXT);
            if (sharing) {
                transmitSelectedLocation();
            } else {
                disableLocationSharing();
            }
        });

        updateLocationBtn.addActionListener(e -> {
            if (chooseLocation() && locationShareToggle.isSelected()) transmitSelectedLocation();
        });

        stepNextBtn.setBackground(GermsColors.PRIMARY_RED);
        stepNextBtn.setForeground(Color.WHITE);
        stepNextBtn.setFont(stepNextBtn.getFont().deriveFont(Font.BOLD));
        stepNextBtn.addActionListener(e -> advanceSelectedAssignment());

        trackingBox.add(locationShareToggle);
        trackingBox.add(updateLocationBtn);
        trackingBox.add(trackingStatus);
        trackingBox.add(Box.createHorizontalStrut(16));
        trackingBox.add(stepNextBtn);

        bottom.add(trackingBox, BorderLayout.NORTH);

        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setOpaque(false);
        deploymentStatusMsg.setForeground(GermsColors.SECONDARY_TEXT);
        statusRow.add(deploymentStatusMsg, BorderLayout.WEST);
        bottom.add(statusRow, BorderLayout.SOUTH);

        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildNotificationsTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        notifTable.setRowHeight(28);
        notifTable.removeColumn(notifTable.getColumnModel().getColumn(0)); // Hide ID
        notifTable.setAutoCreateRowSorter(true);

        DefaultTableCellRenderer prioRenderer = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, val, sel, foc, r, c);
                if (!sel && val != null) {
                    String p = val.toString().toLowerCase();
                    setForeground(GermsColors.forSeverity(p));
                    setFont(getFont().deriveFont(Font.BOLD));
                }
                return this;
            }
        };
        notifTable.getColumnModel().getColumn(0).setCellRenderer(prioRenderer);

        panel.add(new JScrollPane(notifTable), BorderLayout.CENTER);

        // Notifications Controls
        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.setOpaque(false);

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        leftActions.setOpaque(false);

        audibleToggle.setSelected(!alarms.isMuted());
        audibleToggle.addActionListener(e -> {
            alarms.setMuted(!audibleToggle.isSelected());
            alarmStatus.setText(audibleToggle.isSelected() ? "Alarm: Audio Enabled" : "Alarm: Muted");
        });

        JButton ackBtn = new JButton("Acknowledge Alert");
        ackBtn.addActionListener(e -> acknowledgeSelected());

        JButton silenceBtn = new JButton("Silence Active Siren");
        silenceBtn.addActionListener(e -> {
            alarms.stopAlarm();
            alarmStatus.setText("Alarm: Silenced");
            alarmStatus.setForeground(GermsColors.SECONDARY_TEXT);
        });

        leftActions.add(audibleToggle);
        leftActions.add(ackBtn);
        leftActions.add(silenceBtn);

        JPanel testButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        testButtons.setOpaque(false);
        testButtons.add(new JLabel("Test Sirens:"));

        JButton testLow = new JButton("Low");
        testLow.addActionListener(e -> alarms.triggerAlarm("test-low", "low", true, true));
        JButton testMod = new JButton("Moderate");
        testMod.addActionListener(e -> alarms.triggerAlarm("test-mod", "moderate", true, true));
        JButton testHigh = new JButton("High");
        testHigh.addActionListener(e -> alarms.triggerAlarm("test-high", "high", true, true));
        JButton testCrit = new JButton("Critical Siren");
        testCrit.addActionListener(e -> alarms.triggerAlarm("test-crit", "critical", true, true));

        testButtons.add(testLow);
        testButtons.add(testMod);
        testButtons.add(testHigh);
        testButtons.add(testCrit);

        JPanel controlBar = new JPanel(new BorderLayout());
        controlBar.setOpaque(false);
        controlBar.add(leftActions, BorderLayout.WEST);
        controlBar.add(testButtons, BorderLayout.EAST);
        footer.add(controlBar, BorderLayout.NORTH);

        notifStatusMsg.setForeground(GermsColors.SECONDARY_TEXT);
        footer.add(notifStatusMsg, BorderLayout.SOUTH);

        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    @Override public void addNotify() {
        super.addNotify();
        if (!notifPoll.isRunning()) notifPoll.start();
        loadDeployments();
        loadNotifications();
    }

    @Override public void removeNotify() {
        notifPoll.stop();
        if (locationShareToggle.isSelected()) {
            locationShareToggle.setSelected(false);
            disableLocationSharing();
        }
        super.removeNotify();
    }

    public void loadDeployments() {
        deploymentStatusMsg.setText("Loading missions...");
        BackgroundTask.run(() -> {
            return ctx.rest.select("incident_assignments",
                    "select=id,role_name,status,assigned_at,incidents(incident_no,title,location_text)&responder_id=eq." + user.id() + "&order=assigned_at.desc");
        }, rows -> {
            deploymentModel.setRowCount(0);
            if (rows != null && rows.isArray()) {
                for (JsonNode a : rows) {
                    JsonNode inc = a.path("incidents");
                    String incTitle = inc.hasNonNull("title") ? (inc.path("incident_no").asText() + " " + inc.path("title").asText()) : "Incident";
                    String dest = inc.hasNonNull("location_text") ? inc.path("location_text").asText() : "Command Staging Area";

                    String date = a.path("assigned_at").asText();
                    if (date.length() > 19) date = date.substring(0, 19).replace('T', ' ');

                    deploymentModel.addRow(new Object[]{
                            a.path("id").asText(),
                            incTitle,
                            a.path("role_name").asText("Assigned Responder"),
                            a.path("status").asText("assigned").toUpperCase(),
                            dest,
                            date
                    });
                }
            }
            deploymentStatusMsg.setText(deploymentModel.getRowCount() + " deployment record(s) on file.");
            if (deploymentTable.getRowCount() > 0 && deploymentTable.getSelectedRow() < 0) {
                deploymentTable.setRowSelectionInterval(0, 0);
            }
        }, err -> deploymentStatusMsg.setText("Could not load deployments: " + err.getMessage()));
    }

    public void loadNotifications() {
        BackgroundTask.run(() -> {
            return ctx.rest.select("notifications",
                    "select=id,priority,title,body,created_at,notification_receipts(acknowledged_at)&recipient_id=eq." + user.id() + "&order=created_at.desc&limit=50");
        }, rows -> {
            notifModel.setRowCount(0);
            boolean hasUnackedCritical = false;

            if (rows != null && rows.isArray()) {
                for (JsonNode n : rows) {
                    String prio = n.path("priority").asText("normal").toUpperCase();
                    JsonNode receipt = n.path("notification_receipts");
                    boolean acked = false;
                    if (receipt.isArray() && !receipt.isEmpty()) {
                        acked = receipt.get(0).hasNonNull("acknowledged_at");
                    } else if (receipt.isObject() && receipt.hasNonNull("acknowledged_at")) {
                        acked = true;
                    }

                    String notifId = n.path("id").asText();
                    if (!acked) {
                        if ("CRITICAL".equalsIgnoreCase(prio) || "HIGH".equalsIgnoreCase(prio)) {
                            hasUnackedCritical = true;
                            alarms.triggerAlarm(notifId, prio, true, audibleToggle.isSelected());
                        }
                    }

                    String date = n.path("created_at").asText();
                    if (date.length() > 19) date = date.substring(0, 19).replace('T', ' ');

                    notifModel.addRow(new Object[]{
                            notifId,
                            prio,
                            n.path("title").asText(),
                            n.path("body").asText(""),
                            date,
                            acked ? "Acknowledged" : "NEW (UNACKNOWLEDGED)"
                    });
                }
            }
            if (hasUnackedCritical) {
                alarmStatus.setText("Alarm: ACTIVE ALERT!");
                alarmStatus.setForeground(GermsColors.CRITICAL);
            } else {
                alarmStatus.setText("Alarm: Idle");
                alarmStatus.setForeground(GermsColors.SECONDARY_TEXT);
            }
            notifStatusMsg.setText(notifModel.getRowCount() + " notification(s) on file.");
        }, err -> notifStatusMsg.setText("Could not load notifications: " + err.getMessage()));
    }

    private void advanceSelectedAssignment() {
        int r = deploymentTable.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Please select an assignment to advance.", "Notice", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = deploymentTable.convertRowIndexToModel(r);
        String assignmentId = String.valueOf(deploymentModel.getValueAt(modelRow, 0));
        String currentStatus = String.valueOf(deploymentModel.getValueAt(modelRow, 3)).toLowerCase();

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

    private void acknowledgeSelected() {
        int r = notifTable.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Select a notification to acknowledge.", "Notice", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = notifTable.convertRowIndexToModel(r);
        String notifId = String.valueOf(notifModel.getValueAt(modelRow, 0));

        alarms.stopAlarm();
        BackgroundTask.run(() -> {
            ctx.rest.rpc("acknowledge_notification", Map.of("p_id", UUID.fromString(notifId)));
            return Boolean.TRUE;
        }, ok -> {
            loadNotifications();
        }, err -> loadNotifications());
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
            trackingStatus.setText(String.format("Sharing: %s (%.4f, %.4f)",
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
        trackingStatus.setText(String.format("Selected: %s (%.4f, %.4f)",
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
            trackingStatus.setText("Could not disable sharing: " + err.getMessage());
            trackingStatus.setForeground(GermsColors.CRITICAL);
        });
    }
}
