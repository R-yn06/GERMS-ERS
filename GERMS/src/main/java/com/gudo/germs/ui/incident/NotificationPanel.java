package com.gudo.germs.ui.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.services.AlarmService;
import com.gudo.germs.ui.RefreshablePanel;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Map;
import java.util.UUID;

/**
 * Emergency Notifications & Alarm Center.
 * Displays persistent incident deployment alerts and severity alarms.
 * Provides instant alarm silencing, acknowledgment, audible preference configuration,
 * and audio alarm testing.
 */
public final class NotificationPanel extends JPanel implements RefreshablePanel {
    private final AppContext ctx;
    private final AlarmService alarms = AlarmService.get();

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Priority", "Alert", "Details", "Recipient", "Incident / Location",
                    "Responder Status", "Command Status", "Timestamp"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);

    private final JCheckBox audibleToggle = new JCheckBox("Enable Audible Alarms", true);
    private final JLabel alarmStatus = new JLabel("Alarm: Idle");
    private final JLabel footerStatus = new JLabel(" ");
    private String activeAlertId;

    public NotificationPanel(AppContext ctx) {
        super(new BorderLayout(0, 12));
        this.ctx = ctx;
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        add(buildHeader(), BorderLayout.NORTH);

        table.setRowHeight(26);
        table.removeColumn(table.getColumnModel().getColumn(0)); // Hide ID
        table.setAutoCreateRowSorter(true);
        add(new JScrollPane(table), BorderLayout.CENTER);

        add(buildFooter(), BorderLayout.SOUTH);

        loadNotifications();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Notifications & Emergency Alarms");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel desc = new JLabel("System-wide responder alerts. Review the recipient and incident, then acknowledge to close the alert.");
        desc.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel p = new JPanel(new GridLayout(0, 1, 0, 2));
        p.setOpaque(false);
        p.add(title);
        p.add(desc);
        header.add(p, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        JButton silenceBtn = new JButton("Silence / Dismiss Alarm");
        silenceBtn.setBackground(GermsColors.PRIMARY_RED);
        silenceBtn.setForeground(Color.WHITE);
        silenceBtn.setFont(silenceBtn.getFont().deriveFont(Font.BOLD));
        silenceBtn.addActionListener(e -> {
            alarms.stopAlarm();
            alarmStatus.setText("Alarm: Silenced");
        });

        JButton ackBtn = new JButton("Acknowledge Selected");
        ackBtn.addActionListener(e -> acknowledgeSelected());

        audibleToggle.setSelected(!alarms.isMuted());
        audibleToggle.addActionListener(e -> {
            alarms.setMuted(!audibleToggle.isSelected());
            alarmStatus.setText(audibleToggle.isSelected() ? "Alarm: Audio Enabled" : "Alarm: Muted");
        });

        actions.add(alarmStatus);
        actions.add(audibleToggle);
        actions.add(silenceBtn);
        actions.add(ackBtn);

        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JPanel buildFooter() {
        JPanel f = new JPanel(new BorderLayout());
        f.setOpaque(false);
        f.add(footerStatus, BorderLayout.WEST);

        // Sound Test Buttons
        JPanel testButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        testButtons.setOpaque(false);
        testButtons.add(new JLabel("Test Emergency Tone:"));

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

        f.add(testButtons, BorderLayout.EAST);
        return f;
    }

    @Override public void addNotify() {
        super.addNotify();
        loadNotifications();
    }

    @Override public void removeNotify() {
        super.removeNotify();
    }

    @Override
    public void refreshData() {
        loadNotifications();
    }

    public void loadNotifications() {
        BackgroundTask.run(() -> {
            return ctx.rest.select("notifications",
                    "select=id,priority,title,body,created_at,profiles!notifications_recipient_id_fkey(full_name),incidents(incident_no,title,location_text),notification_receipts(acknowledged_at),admin_notification_receipts(acknowledged_at)&order=created_at.desc&limit=100");
        }, rows -> {
            model.setRowCount(0);
            boolean hasUnacknowledgedAlerts = false;
            JsonNode latestUnacknowledged = null;

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
                    JsonNode adminReceipt = n.path("admin_notification_receipts");
                    boolean commandAcknowledged = adminReceipt.isArray()
                            ? !adminReceipt.isEmpty() && adminReceipt.get(0).hasNonNull("acknowledged_at")
                            : adminReceipt.isObject() && adminReceipt.hasNonNull("acknowledged_at");
                    if (!commandAcknowledged) {
                        hasUnacknowledgedAlerts = true;
                        if (latestUnacknowledged == null) latestUnacknowledged = n;
                    }

                    String date = n.path("created_at").asText();
                    if (date.length() > 19) date = date.substring(0, 19).replace('T', ' ');

                    JsonNode recipient = n.path("profiles");
                    JsonNode incident = n.path("incidents");
                    String incidentName = incident.hasNonNull("incident_no")
                            ? incident.path("incident_no").asText() + " " + incident.path("title").asText()
                            : incident.path("title").asText("General alert");
                    String location = incident.path("location_text").asText();
                    if (!location.isBlank()) incidentName += " — " + location;

                    model.addRow(new Object[]{
                            notifId,
                            prio,
                            n.path("title").asText(),
                            n.path("body").asText(""),
                            recipient.path("full_name").asText("Responder"),
                            incidentName,
                            acked ? "Acknowledged" : "Not acknowledged",
                            commandAcknowledged ? "Acknowledged" : "Needs command review",
                            date
                    });
                }
            }
            if (latestUnacknowledged != null) {
                activeAlertId = latestUnacknowledged.path("id").asText();
                alarms.triggerAlarm(activeAlertId,
                        latestUnacknowledged.path("priority").asText("normal"), true, audibleToggle.isSelected());
            } else if (activeAlertId != null) {
                alarms.stopAlarm();
                activeAlertId = null;
            }
            if (hasUnacknowledgedAlerts) {
                alarmStatus.setText("Alarm: ACTIVE ALERT!");
                alarmStatus.setForeground(GermsColors.CRITICAL);
            } else {
                alarmStatus.setText("Alarm: Idle");
                alarmStatus.setForeground(GermsColors.SECONDARY_TEXT);
            }
            footerStatus.setText(model.getRowCount() == 0
                    ? "No responder alerts have been generated yet."
                    : model.getRowCount() + " system alert(s) · " + (hasUnacknowledgedAlerts
                            ? "acknowledgment required"
                            : "all acknowledged"));
        }, err -> footerStatus.setText("Could not load notifications: " + err.getMessage()));
    }

    private void acknowledgeSelected() {
        int r = table.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Select a notification to acknowledge.", "Notice", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = table.convertRowIndexToModel(r);
        String notifId = String.valueOf(model.getValueAt(modelRow, 0));
        if ("Acknowledged".equals(model.getValueAt(modelRow, 7))) {
            footerStatus.setText("The selected alert has already been acknowledged by command.");
            return;
        }

        BackgroundTask.run(() -> {
            ctx.rest.rpc("admin_acknowledge_notification", Map.of("p_id", UUID.fromString(notifId)));
            return Boolean.TRUE;
        }, ok -> {
            if (notifId.equals(activeAlertId)) {
                alarms.stopAlarm();
                activeAlertId = null;
            }
            loadNotifications();
        }, err -> footerStatus.setText("Could not acknowledge alert: " + err.getMessage()));
    }
}

