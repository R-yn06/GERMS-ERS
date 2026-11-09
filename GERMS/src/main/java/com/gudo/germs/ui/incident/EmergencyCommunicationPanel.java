package com.gudo.germs.ui.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Incident;
import com.gudo.germs.models.Profile;
import com.gudo.germs.models.Role;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Incident-Specific Emergency Coordination & Communication Channel.
 * Facilitates operational messaging, urgent broadcasts, and tactical team announcements
 * between Command Staff and deployed emergency responders.
 */
public final class EmergencyCommunicationPanel extends JPanel {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final AppContext ctx;
    private final Profile user;
    private final boolean isAdmin;

    private final JComboBox<Incident> incidentPicker = new JComboBox<>();
    private final DefaultListModel<String> messageListModel = new DefaultListModel<>();
    private final JList<String> messageList = new JList<>(messageListModel);
    private final JTextField inputField = new JTextField();
    private final JComboBox<String> typePicker = new JComboBox<>(new String[]{"regular", "announcement", "urgent"});
    private final JButton sendBtn = new JButton("Send Broadcast");
    private final JLabel channelHeader = new JLabel("Select an active incident channel above.");
    private final Timer pollTimer = new Timer(4000, e -> loadMessages());

    public EmergencyCommunicationPanel(AppContext ctx, Profile user) {
        super(new BorderLayout(0, 10));
        this.ctx = ctx;
        this.user = user;
        this.isAdmin = user.role() == Role.ADMIN;
        setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildChatArea(), BorderLayout.CENTER);
        add(buildInputBar(), BorderLayout.SOUTH);

        pollTimer.setCoalesce(true);
        loadIncidents();
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setOpaque(false);

        JPanel left = new JPanel(new GridLayout(0, 1, 0, 2));
        left.setOpaque(false);
        JLabel title = new JLabel("Emergency Communication Channel");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        left.add(title);
        left.add(channelHeader);
        channelHeader.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        right.add(new JLabel("Incident Channel:"));
        incidentPicker.setPreferredSize(new Dimension(320, 32));
        incidentPicker.addActionListener(e -> loadMessages());
        right.add(incidentPicker);

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadIncidents());
        right.add(refresh);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JComponent buildChatArea() {
        messageList.setFont(new Font("Monospaced", Font.PLAIN, 13));
        messageList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(messageList);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(210, 214, 220)));
        return scroll;
    }

    private JPanel buildInputBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);
        left.add(new JLabel("Type:"));
        left.add(typePicker);

        sendBtn.setBackground(GermsColors.PRIMARY_RED);
        sendBtn.setForeground(Color.WHITE);
        sendBtn.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());

        bar.add(left, BorderLayout.WEST);
        bar.add(inputField, BorderLayout.CENTER);
        bar.add(sendBtn, BorderLayout.EAST);
        return bar;
    }

    @Override public void addNotify() {
        super.addNotify();
        if (!pollTimer.isRunning()) pollTimer.start();
        loadIncidents();
    }

    @Override public void removeNotify() {
        pollTimer.stop();
        super.removeNotify();
    }

    private void loadIncidents() {
        BackgroundTask.run(() -> ctx.incidents.visibleTo(false), all -> {
            Incident selected = (Incident) incidentPicker.getSelectedItem();
            String keepId = selected != null ? selected.id() : null;

            incidentPicker.removeAllItems();
            List<Incident> active = all.stream().filter(i -> i.status().isActive()).toList();
            for (Incident i : active) {
                incidentPicker.addItem(i);
                if (i.id().equals(keepId)) {
                    incidentPicker.setSelectedItem(i);
                }
            }
            loadMessages();
        }, err -> {});
    }

    private void loadMessages() {
        Incident inc = (Incident) incidentPicker.getSelectedItem();
        if (inc == null) {
            channelHeader.setText("No active incident channel selected.");
            messageListModel.clear();
            return;
        }

        channelHeader.setText("Active Channel: " + inc.incidentNo() + " \u2014 " + inc.title() + " (" + inc.severity().toUpperCase() + ")");

        BackgroundTask.run(() -> {
            return ctx.rest.select("incident_messages",
                    "select=id,message,message_type,created_at,profiles(full_name,role)&incident_id=eq." + inc.id() + "&order=created_at.asc");
        }, rows -> {
            messageListModel.clear();
            if (rows != null && rows.isArray()) {
                for (JsonNode m : rows) {
                    String time = m.path("created_at").asText();
                    try {
                        time = TIME_FMT.format(Instant.parse(time));
                    } catch (Exception ignored) {}

                    JsonNode p = m.path("profiles");
                    String sender = p.hasNonNull("full_name") ? p.path("full_name").asText() : "Command Staff";
                    String role = p.path("role").asText("user").toUpperCase();
                    String type = m.path("message_type").asText("regular");

                    String prefix = "";
                    if ("urgent".equalsIgnoreCase(type)) prefix = "[URGENT] ";
                    else if ("announcement".equalsIgnoreCase(type)) prefix = "[BROADCAST] ";

                    messageListModel.addElement(String.format("[%s] <%s \u00B7 %s> %s%s",
                            time, sender, role, prefix, m.path("message").asText()));
                }
            }
            if (!messageListModel.isEmpty()) {
                messageList.ensureIndexIsVisible(messageListModel.size() - 1);
            }
        }, err -> {});
    }

    private void sendMessage() {
        Incident inc = (Incident) incidentPicker.getSelectedItem();
        String text = inputField.getText().trim();
        if (inc == null || text.isEmpty()) return;

        String type = (String) typePicker.getSelectedItem();
        sendBtn.setEnabled(false);

        BackgroundTask.run(() -> {
            ctx.rest.rpc("send_incident_message", Map.of(
                    "p_incident", UUID.fromString(inc.id()),
                    "p_message", text,
                    "p_type", type != null ? type : "regular"
            ));
            return Boolean.TRUE;
        }, ok -> {
            inputField.setText("");
            sendBtn.setEnabled(true);
            loadMessages();
        }, err -> {
            sendBtn.setEnabled(true);
            JOptionPane.showMessageDialog(this, "Message delivery failed: " + err.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        });
    }
}

