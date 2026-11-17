package com.gudo.germs.ui.incident;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.services.AlarmService;
import com.gudo.germs.ui.components.LocationPickerDialog;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

/**
 * My Deployments, Mission Command, Notifications & Emergency Alarms.
 * Combines incident assignments, operational location sharing, and real-time
 * audible alarms.
 */
public final class MyDeploymentsPanel extends JPanel implements com.gudo.germs.ui.RefreshablePanel {
    private final AppContext ctx;
    private final Profile user;
    private final Consumer<String> onAcknowledged;
    private final AlarmService alarms = AlarmService.get();

    // Tabs
    private final JTabbedPane tabbedPane = new JTabbedPane();

    // Deployment Tab Components
    private final DefaultTableModel deploymentModel = new DefaultTableModel(
            new String[] { "Assignment ID", "Incident", "Role", "Status", "Destination", "Assigned At" }, 0) {
        /** Reports whether the table cell can be edited. */
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
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
            new String[] { "ID", "Incident ID", "Kind", "Priority", "Title", "Incident / Location", "Timestamp",
                    "Status" },
            0) {
        /** Reports whether the table cell can be edited. */
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable notifTable = new JTable(notifModel);
    private final JCheckBox audibleToggle = new JCheckBox("Enable Audible Alarms", true);
    private final JLabel alarmStatus = new JLabel("Alarm: Idle");
    private final JLabel notifStatusMsg = new JLabel(" ");

    /** Creates a my deployments panel instance. */
    public MyDeploymentsPanel(AppContext ctx, Profile user, Consumer<String> onAcknowledged) {
        super(new BorderLayout(0, 10));
        this.ctx = ctx;
        this.user = user;
        this.onAcknowledged = onAcknowledged;
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        add(buildHeader(), BorderLayout.NORTH);

        tabbedPane.addTab("Active Deployments & Missions", buildDeploymentsTab());
        tabbedPane.addTab("Notifications & Emergency Alarms", buildNotificationsTab());
        tabbedPane.setFont(tabbedPane.getFont().deriveFont(Font.BOLD, 13f));
        add(tabbedPane, BorderLayout.CENTER);

        loadDeployments();
        loadNotifications();
    }

    /** Builds header. */
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 10));
        header.setOpaque(false);

        // Row 1: title + subtitle
        JLabel title = new JLabel("My Deployments & Mission Center");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel desc = new JLabel(
                "Manage assigned deployments, location broadcast, and monitor emergency alarm dispatch feeds.");
        desc.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel titles = new JPanel(new GridLayout(0, 1, 0, 2));
        titles.setOpaque(false);
        titles.add(title);
        titles.add(desc);
        header.add(titles, BorderLayout.NORTH);

        // Row 2: alarm status + action buttons
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

        // BoxLayout keeps everything in one row at its natural size (no wrapping).
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        actions.add(silenceQuick);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(refreshAll);
        actions.add(Box.createHorizontalStrut(16));
        actions.add(alarmStatus);
        actions.add(Box.createHorizontalGlue()); // pushes everything to the left

        header.add(actions, BorderLayout.CENTER);
        return header;
    }

    /** Builds deployments tab. */
    private JPanel buildDeploymentsTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        deploymentTable.setRowHeight(28);
        deploymentTable.removeColumn(deploymentTable.getColumnModel().getColumn(0)); // Hide Assignment ID
        deploymentTable.setAutoCreateRowSorter(true);

        DefaultTableCellRenderer statusRenderer = new DefaultTableCellRenderer() {
            /** Returns table cell renderer component. */
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int r,
                    int c) {
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

        JPanel trackingBox = new JPanel(new WrapLayout(FlowLayout.LEFT, 12, 6));
        ;
        trackingBox.setOpaque(false);

        locationShareToggle.setOpaque(false);
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
            if (chooseLocation() && locationShareToggle.isSelected())
                transmitSelectedLocation();
        });

        stepNextBtn.setBackground(GermsColors.PRIMARY_RED);
        stepNextBtn.setForeground(Color.WHITE);
        stepNextBtn.setFont(stepNextBtn.getFont().deriveFont(Font.BOLD));
        stepNextBtn.addActionListener(e -> advanceSelectedAssignment());

        // The status label changes length, so it goes last and the buttons don't shift.
        trackingBox.add(locationShareToggle);
        trackingBox.add(updateLocationBtn);
        trackingBox.add(stepNextBtn);
        trackingBox.add(trackingStatus);

        panel.add(trackingBox, BorderLayout.NORTH); // controls now above the table

        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setOpaque(false);
        deploymentStatusMsg.setForeground(GermsColors.SECONDARY_TEXT);
        statusRow.add(deploymentStatusMsg, BorderLayout.WEST);
        panel.add(statusRow, BorderLayout.SOUTH);
        return panel;
    }

    /** Builds notifications tab. */
    private JPanel buildNotificationsTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        notifTable.setRowHeight(28);
        notifTable.removeColumn(notifTable.getColumnModel().getColumn(0)); // Hide ID
        notifTable.removeColumn(notifTable.getColumnModel().getColumn(0)); // Hide incident ID
        notifTable.removeColumn(notifTable.getColumnModel().getColumn(0)); // Hide kind
        notifTable.setAutoCreateRowSorter(true);

        DefaultTableCellRenderer prioRenderer = new DefaultTableCellRenderer() {
            /** Returns table cell renderer component. */
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int r,
                    int c) {
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

        // ---- Controls ----
        audibleToggle.setSelected(!alarms.isMuted());
        audibleToggle.setOpaque(false);
        audibleToggle.addActionListener(e -> {
            alarms.setMuted(!audibleToggle.isSelected());
            alarmStatus.setText(audibleToggle.isSelected() ? "Alarm: Audio Enabled" : "Alarm: Muted");
        });

        JButton acceptBtn = new JButton("Accept Request");
        acceptBtn.addActionListener(e -> respondToSelectedIncident("accepted"));
        JButton declineBtn = new JButton("Decline Request");
        declineBtn.addActionListener(e -> respondToSelectedIncident("declined"));
        JButton ackBtn = new JButton("Acknowledge Alert");
        ackBtn.addActionListener(e -> acknowledgeSelected());
        JButton silenceBtn = new JButton("Silence Active Siren");
        silenceBtn.addActionListener(e -> {
            alarms.stopAlarm();
            alarmStatus.setText("Alarm: Silenced");
            alarmStatus.setForeground(GermsColors.SECONDARY_TEXT);
        });

        JButton testLow = new JButton("Low");
        testLow.addActionListener(e -> alarms.triggerAlarm("test-low", "low", true, true));
        JButton testMod = new JButton("Moderate");
        testMod.addActionListener(e -> alarms.triggerAlarm("test-mod", "moderate", true, true));
        JButton testHigh = new JButton("High");
        testHigh.addActionListener(e -> alarms.triggerAlarm("test-high", "high", true, true));
        JButton testCrit = new JButton("Critical Siren");
        testCrit.addActionListener(e -> alarms.triggerAlarm("test-crit", "critical", true, true));

        // Row 1: main actions
        JPanel actionsRow = row();
        actionsRow.add(acceptBtn);
        actionsRow.add(Box.createHorizontalStrut(8));
        actionsRow.add(declineBtn);
        actionsRow.add(Box.createHorizontalStrut(8));
        actionsRow.add(ackBtn);
        actionsRow.add(Box.createHorizontalStrut(8));
        actionsRow.add(silenceBtn);
        actionsRow.add(Box.createHorizontalStrut(16));
        actionsRow.add(audibleToggle);
        actionsRow.add(Box.createHorizontalGlue());

        // Row 2: test sirens
        JPanel testRow = row();
        testRow.add(new JLabel("Test Sirens:"));
        JButton[] tests = { testLow, testMod, testHigh, testCrit };
        for (JButton b : tests) {
            testRow.add(Box.createHorizontalStrut(8));
            testRow.add(b);
        }
        testRow.add(Box.createHorizontalGlue());

        JPanel controls = new JPanel(new GridLayout(0, 1, 0, 2));
        controls.setOpaque(false);
        controls.add(actionsRow);
        controls.add(testRow);

        panel.add(controls, BorderLayout.NORTH); // controls now above the table

        notifStatusMsg.setForeground(GermsColors.SECONDARY_TEXT);
        panel.add(notifStatusMsg, BorderLayout.SOUTH);
        return panel;
    }

    /** One left-aligned horizontal row of controls at natural size. */
    private static JPanel row() {
        JPanel p = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 4));
        p.setOpaque(false);
        return p;
    }

    /** Loads deployments and notifications when this panel is added to the hierarchy. */
    @Override
    public void addNotify() {
        super.addNotify();
        loadDeployments();
        loadNotifications();
    }

    /** Disables active location sharing when the panel is removed. */
    @Override
    public void removeNotify() {
        if (locationShareToggle.isSelected()) {
            locationShareToggle.setSelected(false);
            disableLocationSharing();
        }

        super.removeNotify();
    }

    /** Refreshes data. */
    @Override
    public void refreshData() {
        loadDeployments();
        loadNotifications();
    }

    /** Loads deployments. */
    public void loadDeployments() {
        deploymentStatusMsg.setText("Loading missions...");
        BackgroundTask.run(() -> {
            return ctx.rest.select("incident_assignments",
                    "select=id,role_name,status,assigned_at,incidents(incident_no,title,location_text)&responder_id=eq."
                            + user.id() + "&order=assigned_at.desc");
        }, rows -> {
            deploymentModel.setRowCount(0);
            if (rows != null && rows.isArray()) {
                for (JsonNode a : rows) {
                    JsonNode inc = a.path("incidents");
                    String incTitle = inc.hasNonNull("title")
                            ? (inc.path("incident_no").asText() + " " + inc.path("title").asText())
                            : "Incident";
                    String dest = inc.hasNonNull("location_text") ? inc.path("location_text").asText()
                            : "Command Staging Area";

                    String date = a.path("assigned_at").asText();
                    if (date.length() > 19)
                        date = date.substring(0, 19).replace('T', ' ');

                    deploymentModel.addRow(new Object[] {
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

    /** Loads notifications. */
    public void loadNotifications() {
        BackgroundTask.run(() -> {
            return ctx.rest.select("notifications",
                    "select=id,incident_id,kind,priority,title,body,created_at,notification_receipts(acknowledged_at)&recipient_id=eq."
                            + user.id() + "&order=created_at.desc&limit=50");
        }, rows -> {
            notifModel.setRowCount(0);
            boolean hasUnacknowledgedAlerts = false;

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
                        hasUnacknowledgedAlerts = true;
                    }

                    String date = n.path("created_at").asText();
                    if (date.length() > 19)
                        date = date.substring(0, 19).replace('T', ' ');

                    notifModel.addRow(new Object[] {
                            notifId,
                            n.path("incident_id").asText(""),
                            n.path("kind").asText(""),
                            prio,
                            n.path("title").asText(),
                            n.path("body").asText(""),
                            date,
                            acked ? "Acknowledged" : "NEW (UNACKNOWLEDGED)"
                    });
                }
            }
            if (hasUnacknowledgedAlerts) {
                alarmStatus.setText("Alarm: ACTIVE ALERT!");
                alarmStatus.setForeground(GermsColors.CRITICAL);
            } else {
                alarmStatus.setText("Alarm: Idle");
                alarmStatus.setForeground(GermsColors.SECONDARY_TEXT);
            }
            notifStatusMsg.setText(notifModel.getRowCount() + " notification(s) on file.");
        }, err -> notifStatusMsg.setText("Could not load notifications: " + err.getMessage()));
    }

    /** Advances the selected deployment through its next allowed status. */
    private void advanceSelectedAssignment() {
        int r = deploymentTable.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Please select an assignment to advance.", "Notice",
                    JOptionPane.WARNING_MESSAGE);
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
            JOptionPane.showMessageDialog(this,
                    "Assignment is already in final status (" + currentStatus.toUpperCase() + ").", "Notice",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Advance deployment status to: " + nextStatus.toUpperCase() + "?",
                "Advance Mission Status", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION)
            return;

        BackgroundTask.run(() -> {
            ctx.rest.rpc("update_assignment_status", Map.of(
                    "p_assignment", UUID.fromString(assignmentId),
                    "p_to", nextStatus,
                    "p_note", "Status advanced by responder: " + user.fullName()));
            return Boolean.TRUE;
        }, ok -> {
            loadDeployments();
            JOptionPane.showMessageDialog(this, "Mission status updated to " + nextStatus.toUpperCase() + ".",
                    "Status Updated", JOptionPane.INFORMATION_MESSAGE);
        }, err -> JOptionPane.showMessageDialog(this, "Update failed: " + err.getMessage(), "Error",
                JOptionPane.ERROR_MESSAGE));
    }

    /** Acknowledges the selected notification and refreshes the notification list. */
    private void acknowledgeSelected() {
        int r = notifTable.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Select a notification to acknowledge.", "Notice",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = notifTable.convertRowIndexToModel(r);
        String notifId = String.valueOf(notifModel.getValueAt(modelRow, 0));

        BackgroundTask.run(() -> {
            ctx.rest.rpc("acknowledge_notification", Map.of("p_id", UUID.fromString(notifId)));
            return Boolean.TRUE;
        }, ok -> {
            onAcknowledged.accept(notifId);
            loadNotifications();
        }, err -> notifStatusMsg.setText("Could not acknowledge notification: " + err.getMessage()));
    }

    /** Accepts or declines the selected deployment request and refreshes notifications. */
    private void respondToSelectedIncident(String decision) {
        int selected = notifTable.getSelectedRow();
        if (selected < 0) {
            notifStatusMsg.setText("Select a deployment request first.");
            return;
        }
        int row = notifTable.convertRowIndexToModel(selected);
        String kind = String.valueOf(notifModel.getValueAt(row, 2));
        String incidentId = String.valueOf(notifModel.getValueAt(row, 1));
        if (!"deployment_request".equals(kind) || incidentId.isBlank()) {
            notifStatusMsg.setText("Select a deployment request to accept or decline.");
            return;
        }
        BackgroundTask.run(() -> {
            ctx.rest.rpc("respond_to_incident", Map.of(
                    "p_incident", UUID.fromString(incidentId),
                    "p_decision", decision,
                    "p_note", decision.equals("accepted")
                            ? "Accepted from notification alert"
                            : "Declined from notification alert"));
            return Boolean.TRUE;
        }, ok -> {
            notifStatusMsg.setText("Incident request " + decision + ".");
            loadNotifications();
        }, err -> notifStatusMsg.setText("Could not save response: " + err.getMessage()));
    }

    /** Shares the selected responder location with the incident operations service. */
    private void transmitSelectedLocation() {
        if (!locationShareToggle.isSelected())
            return;
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

    /** Opens the location picker and returns whether the user selected a location. */
    private boolean chooseLocation() {
        LocationPickerDialog.Selection picked = LocationPickerDialog.showDialog(
                SwingUtilities.getWindowAncestor(this), "Choose Operational Location", selectedLocation);
        if (picked == null)
            return false;
        selectedLocation = picked;
        trackingStatus.setText(String.format("Selected: %s (%.4f, %.4f)",
                picked.label(), picked.latitude(), picked.longitude()));
        trackingStatus.setForeground(GermsColors.SECONDARY_TEXT);
        return true;
    }

    /** Disables responder location sharing through the Supabase RPC. */
    private void disableLocationSharing() {
        BackgroundTask.run(() -> {
            ctx.rest.rpc("update_responder_location", Map.of(
                    "p_lat", 0.0,
                    "p_lon", 0.0,
                    "p_accuracy", 0.0,
                    "p_source", "manual",
                    "p_sharing", false));
            return Boolean.TRUE;
        }, ok -> {
            trackingStatus.setText("Location Sharing: Disabled");
            trackingStatus.setForeground(GermsColors.SECONDARY_TEXT);
        }, err -> {
            trackingStatus.setText("Could not disable sharing: " + err.getMessage());
            trackingStatus.setForeground(GermsColors.CRITICAL);
        });
    }

    /** Wraps FlowLayout rows when components exceed the available width. */
    private static final class WrapLayout extends FlowLayout {
        /** Creates a wrap layout instance. */
        WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        /** Computes the preferred size needed to lay out the wrapped components. */
        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }

        /** Computes the minimum size needed to lay out the wrapped components. */
        @Override
        public Dimension minimumLayoutSize(Container target) {
            Dimension d = layoutSize(target, false);
            d.width -= getHgap() + 1;
            return d;
        }

        /** Calculates the wrapped component layout size for the requested sizing mode. */
        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                Container c = target;
                while (c.getSize().width == 0 && c.getParent() != null)
                    c = c.getParent();
                int targetWidth = c.getSize().width;
                if (targetWidth == 0)
                    targetWidth = Integer.MAX_VALUE;

                Insets in = target.getInsets();
                int hgap = getHgap(), vgap = getVgap();
                int maxWidth = targetWidth - (in.left + in.right + hgap * 2);

                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0, rowHeight = 0;
                for (int i = 0; i < target.getComponentCount(); i++) {
                    Component m = target.getComponent(i);
                    if (!m.isVisible())
                        continue;
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                    if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                        addRow(dim, rowWidth, rowHeight);
                        rowWidth = 0;
                        rowHeight = 0;
                    }
                    if (rowWidth != 0)
                        rowWidth += hgap;
                    rowWidth += d.width;
                    rowHeight = Math.max(rowHeight, d.height);
                }
                addRow(dim, rowWidth, rowHeight);
                dim.width += in.left + in.right + hgap * 2;
                dim.height += in.top + in.bottom + vgap * 2;
                return dim;
            }
        }

        /** Adds row. */
        private void addRow(Dimension dim, int rowWidth, int rowHeight) {
            dim.width = Math.max(dim.width, rowWidth);
            if (dim.height > 0)
                dim.height += getVgap();
            dim.height += rowHeight;
        }
    }
}
