package com.gudo.germs.ui.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.models.Role;
import com.gudo.germs.services.AlarmService;
import com.gudo.germs.ui.RefreshablePanel;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * User notification inbox. Responders see their own alerts; administrators see
 * the shared responder feed and track command review separately.
 */
public final class NotificationPanel extends JPanel implements RefreshablePanel {
    private final AppContext ctx;
    private final Profile user;
    private final Consumer<String> onAcknowledged;
    private final boolean isAdmin;
    private final AlarmService alarms = AlarmService.get();

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Priority", "Alert", "Details", "Recipient", "Incident / Location",
                    "Responder Status", "Command Status", "Timestamp"}, 0) {
        /** Reports whether the table cell can be edited. */
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);

    private final JCheckBox audibleToggle = new JCheckBox("Enable Audible Alarms", true);
    private final JLabel alarmStatus = new JLabel("Alarm: Idle");
    private final JLabel footerStatus = new JLabel(" ");
    private String activeAlertId;
    private String pendingNotificationId;

    /** Creates a notification panel instance. */
    public NotificationPanel(AppContext ctx, Profile user, Consumer<String> onAcknowledged) {
        super(new BorderLayout(0, 12));
        this.ctx = ctx;
        this.user = user;
        this.onAcknowledged = onAcknowledged;
        this.isAdmin = user.role() == Role.ADMIN;
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        add(buildHeader(), BorderLayout.NORTH);

        table.setRowHeight(26);
        table.removeColumn(table.getColumnModel().getColumn(0)); // Hide ID
        table.setAutoCreateRowSorter(true);
        add(new JScrollPane(table), BorderLayout.CENTER);

        add(buildFooter(), BorderLayout.SOUTH);
    }

    /** Builds header. */
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel(isAdmin ? "Command Notifications & Alerts" : "My Notifications & Alerts");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel desc = new JLabel(isAdmin
                ? "Review shared responder alerts, identify who needs help, and acknowledge command follow-up."
                : "Notifications stay available wherever you are in GERMS. Select an alert to review or acknowledge it.");
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

    /** Builds footer. */
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

    /** Loads notifications when this panel is added to the component hierarchy. */
    @Override public void addNotify() {
        super.addNotify();
        loadNotifications();
    }

    /** Runs the superclass removal lifecycle for this notification panel. */
    @Override public void removeNotify() {
        super.removeNotify();
    }

    /** Refreshes data. */
    @Override
    public void refreshData() {
        loadNotifications();
    }

    /** Opens notification. */
    public void openNotification(String notificationId) {
        pendingNotificationId = notificationId;
        if (isDisplayable()) loadNotifications();
    }

    /** Loads notifications. */
    public void loadNotifications() {
        BackgroundTask.run(() -> {
            String filter = isAdmin ? "" : "&recipient_id=eq." + user.id();
            String adminReceipt = isAdmin ? ",admin_notification_receipts(acknowledged_at)" : "";
            return ctx.rest.select("notifications",
                    "select=id,priority,title,body,created_at,profiles!notifications_recipient_id_fkey(full_name),incidents(incident_no,title,location_text),notification_receipts(acknowledged_at)"
                            + adminReceipt + filter + "&order=created_at.desc&limit=100");
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
                    boolean commandAcknowledged = isAdmin && receiptAcknowledged(adminReceipt);
                    boolean actionable = isAdmin ? !commandAcknowledged : !acked;
                    if (actionable) {
                        hasUnacknowledgedAlerts = true;
                        if (isAdmin && latestUnacknowledged == null) latestUnacknowledged = n;
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
                            isAdmin ? (acked ? "Acknowledged" : "Not acknowledged") : "—",
                            isAdmin ? (commandAcknowledged ? "Acknowledged" : "Needs command review")
                                    : (acked ? "Acknowledged" : "Needs acknowledgment"),
                            date
                    });
                }
            }
            if (isAdmin && latestUnacknowledged != null) {
                activeAlertId = latestUnacknowledged.path("id").asText();
                alarms.triggerAlarm(activeAlertId,
                        latestUnacknowledged.path("priority").asText("normal"), true, audibleToggle.isSelected());
            } else if (isAdmin && activeAlertId != null) {
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
                    ? (isAdmin ? "No responder alerts have been generated yet." : "You have no notifications yet.")
                    : model.getRowCount() + " system alert(s) · " + (hasUnacknowledgedAlerts
                            ? "acknowledgment required"
                            : "all acknowledged"));
            if (pendingNotificationId != null) {
                boolean selected = false;
                for (int row = 0; row < model.getRowCount(); row++) {
                    if (pendingNotificationId.equals(model.getValueAt(row, 0))) {
                        int viewRow = table.convertRowIndexToView(row);
                        table.setRowSelectionInterval(viewRow, viewRow);
                        table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
                        selected = true;
                        break;
                    }
                }
                if (!selected) footerStatus.setText("That notification is no longer in the recent alert list.");
                pendingNotificationId = null;
            }
        }, err -> footerStatus.setText("Could not load notifications: " + err.getMessage()));
    }

    /** Reports whether a notification receipt contains an acknowledgement timestamp. */
    private static boolean receiptAcknowledged(JsonNode receipt) {
        if (receipt.isArray()) return !receipt.isEmpty() && receipt.get(0).hasNonNull("acknowledged_at");
        return receipt.isObject() && receipt.hasNonNull("acknowledged_at");
    }

    /** Acknowledges the selected notification through Supabase and refreshes the list. */
    private void acknowledgeSelected() {
        int r = table.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Select a notification to acknowledge.", "Notice", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = table.convertRowIndexToModel(r);
        String notifId = String.valueOf(model.getValueAt(modelRow, 0));
        int statusColumn = isAdmin ? 7 : 6;
        if ("Acknowledged".equals(model.getValueAt(modelRow, statusColumn))) {
            footerStatus.setText("The selected alert has already been acknowledged.");
            return;
        }

        BackgroundTask.run(() -> {
            String rpc = isAdmin ? "admin_acknowledge_notification" : "acknowledge_notification";
            ctx.rest.rpc(rpc, Map.of("p_id", UUID.fromString(notifId)));
            return Boolean.TRUE;
        }, ok -> {
            onAcknowledged.accept(notifId);
            loadNotifications();
        }, err -> footerStatus.setText("Could not acknowledge alert: " + err.getMessage()));
    }
}

