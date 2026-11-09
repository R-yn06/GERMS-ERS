package com.gudo.germs.ui.components;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.models.Role;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Public Emergency Announcements & Early Warning Advisories.
 * Used by Red Cross Administrators to broadcast official disaster warnings,
 * weather alerts, and evacuation orders to the public and operational responders.
 */
public final class PublicAnnouncementsPanel extends JPanel {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final AppContext ctx;
    private final Profile user;
    private final boolean readOnly;

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Priority", "Title", "Advisory Summary", "Published Date", "Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JTextArea detailArea = new JTextArea();
    private final JLabel statusLabel = new JLabel(" ");

    public PublicAnnouncementsPanel(AppContext ctx, Profile user, boolean readOnly) {
        super(new BorderLayout(0, 12));
        this.ctx = ctx;
        this.user = user;
        this.readOnly = readOnly;
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        add(buildHeader(), BorderLayout.NORTH);

        table.setRowHeight(25);
        table.removeColumn(table.getColumnModel().getColumn(0)); // Hide ID
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int r = table.getSelectedRow();
                if (r >= 0) {
                    detailArea.setText(String.valueOf(model.getValueAt(table.convertRowIndexToModel(r), 3)));
                }
            }
        });

        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setPreferredSize(new Dimension(300, 160));
        detailScroll.setBorder(BorderFactory.createTitledBorder("Full Advisory Content"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), detailScroll);
        split.setResizeWeight(0.65);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        add(statusLabel, BorderLayout.SOUTH);

        loadAnnouncements();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Public Safety Announcements & Advisories");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel desc = new JLabel("Official emergency alerts, weather warnings, evacuation guidelines, and public disaster updates.");
        desc.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel p = new JPanel(new GridLayout(0, 1, 0, 2));
        p.setOpaque(false);
        p.add(title);
        p.add(desc);
        header.add(p, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        if (!readOnly && user.role() == Role.ADMIN) {
            JButton createBtn = new JButton("+ Post Advisory");
            createBtn.setBackground(GermsColors.PRIMARY_RED);
            createBtn.setForeground(Color.WHITE);
            createBtn.addActionListener(e -> showCreateAnnouncementDialog());
            actions.add(createBtn);
        }

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadAnnouncements());
        actions.add(refresh);

        header.add(actions, BorderLayout.EAST);
        return header;
    }

    public void loadAnnouncements() {
        statusLabel.setText("Querying public announcements...");
        String filter = readOnly ? "&status=eq.published&is_public=eq.true" : "";
        BackgroundTask.run(() -> {
            return ctx.rest.select("emergency_announcements", "select=*&order=published_at.desc" + filter);
        }, rows -> {
            model.setRowCount(0);
            if (rows != null && rows.isArray()) {
                for (JsonNode a : rows) {
                    String date = a.path("published_at").asText();
                    try {
                        date = TIME_FMT.format(Instant.parse(date));
                    } catch (Exception ignored) {}

                    model.addRow(new Object[]{
                            a.path("id").asText(),
                            a.path("priority").asText("normal").toUpperCase(),
                            a.path("title").asText(),
                            a.path("description").asText(),
                            date,
                            a.path("status").asText().toUpperCase()
                    });
                }
            }
            statusLabel.setText(model.getRowCount() + " announcement(s) published.");
            if (table.getRowCount() > 0 && table.getSelectedRow() < 0) {
                table.setRowSelectionInterval(0, 0);
            }
        }, err -> {
            statusLabel.setText("Could not load announcements: " + err.getMessage());
        });
    }

    private void showCreateAnnouncementDialog() {
        JTextField titleField = new JTextField();
        JTextArea descArea = new JTextArea(5, 24);
        descArea.setLineWrap(true);
        JComboBox<String> priorityBox = new JComboBox<>(new String[]{"normal", "high", "critical"});

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Advisory Title:")); form.add(titleField);
        form.add(new JLabel("Priority Level:")); form.add(priorityBox);
        form.add(new JLabel("Message / Instructions:")); form.add(new JScrollPane(descArea));

        int res = JOptionPane.showConfirmDialog(this, form, "Broadcast Emergency Advisory", JOptionPane.OK_CANCEL_OPTION);
        if (res != JOptionPane.OK_OPTION) return;

        String title = titleField.getText().trim();
        String desc = descArea.getText().trim();
        if (title.isEmpty() || desc.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Title and description are required.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        BackgroundTask.run(() -> {
            ctx.rest.insertMinimal("emergency_announcements", Map.of(
                    "title", title,
                    "description", desc,
                    "priority", priorityBox.getSelectedItem(),
                    "is_public", true,
                    "status", "published",
                    "created_by", user.id().toString()
            ));
            return Boolean.TRUE;
        }, ok -> {
            loadAnnouncements();
            JOptionPane.showMessageDialog(this, "Emergency advisory broadcasted successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
        }, err -> JOptionPane.showMessageDialog(this, "Broadcast failed: " + err.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
    }
}

