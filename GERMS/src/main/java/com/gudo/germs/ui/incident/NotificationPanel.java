package com.gudo.germs.ui.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.services.AlarmService;
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
public final class NotificationPanel extends JPanel {
    private final AppContext ctx;
    private final Profile user;
    private final AlarmService alarms = AlarmService.get();

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Priority", "Title", "Incident / Location", "Timestamp", "Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);

    private final JCheckBox audibleToggle = new JCheckBox("Enable Audible Alarms", true);
    private final JLabel alarmStatus = new JLabel("Alarm: Idle");
    private final JLabel footerStatus = new JLabel(" ");
    private final Timer poll = new Timer(5000, e -> loadNotifications());

    public NotificationPanel(AppContext ctx, Profile user) {
        super(new BorderLayout(0, 12));
        this.ctx = ctx;
        this.user = user;
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        add(buildHeader(), BorderLayout.NORTH);

        table.setRowHeight(26);
        table.removeColumn(table.getColumnModel().getColumn(0)); // Hide ID
        table.setAutoCreateRowSorter(true);
        add(new JScrollPane(table), BorderLayout.CENTER);

        add(buildFooter(), BorderLayout.SOUTH);

        poll.setCoalesce(true);
        loadNotifications();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Notifications & Emergency Alarms");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel desc = new JLabel("Incident dispatch alerts, severity-based emergency alarms, and operational notifications.");
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
        if (!poll.isRunning()) poll.start();
        loadNotifications();
    }

    @Override public void removeNotify() {
        poll.stop();
        super.removeNotify();
    }

    public void loadNotifications() {
        BackgroundTask.run(() -> {
            return ctx.rest.select("notifications",
                    "select=id,priority,title,body,created_at,notification_receipts(acknowledged_at)&recipient_id=eq." + user.id() + "&order=created_at.desc&limit=50");
        }, rows -> {
            model.setRowCount(0);
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
                            // Trigger severity alarm if unacknowledged
                            alarms.triggerAlarm(notifId, prio, true, audibleToggle.isSelected());
                        }
                    }

                    String date = n.path("created_at").asText();
                    if (date.length() > 19) date = date.substring(0, 19).replace('T', ' ');

                    model.addRow(new Object[]{
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
            footerStatus.setText(model.getRowCount() + " notification(s) on file.");
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

        alarms.stopAlarm();
        BackgroundTask.run(() -> {
            ctx.rest.rpc("acknowledge_notification", Map.of("p_id", UUID.fromString(notifId)));
            return Boolean.TRUE;
        }, ok -> {
            loadNotifications();
        }, err -> loadNotifications());
    }
}

