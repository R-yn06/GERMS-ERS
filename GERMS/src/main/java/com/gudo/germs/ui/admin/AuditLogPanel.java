package com.gudo.germs.ui.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Activity & Audit Log Viewer.
 * Displays append-only chronological history of all incident updates,
 * command decisions, responder deployments, and administrative actions.
 */
public final class AuditLogPanel extends JPanel {
    private final AppContext ctx;
    private final Profile user;

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Timestamp", "Actor", "Action", "Entity", "Target ID", "Details JSON"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JTextField searchField = new JTextField(16);
    private final JLabel statusLabel = new JLabel(" ");

    public AuditLogPanel(AppContext ctx, Profile user) {
        super(new BorderLayout(0, 12));
        this.ctx = ctx;
        this.user = user;
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        add(buildHeader(), BorderLayout.NORTH);

        table.setRowHeight(24);
        table.setAutoCreateRowSorter(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        loadLogs();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Activity & Audit Logs");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel desc = new JLabel("Tamper-evident, append-only operational audit trail of incident commands, personnel assignments, and system events.");
        desc.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel p = new JPanel(new GridLayout(0, 1, 0, 2));
        p.setOpaque(false);
        p.add(title);
        p.add(desc);
        header.add(p, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        actions.add(new JLabel("Search Event:"));
        searchField.addActionListener(e -> loadLogs());
        actions.add(searchField);

        JButton inspectBtn = new JButton("View Details");
        inspectBtn.addActionListener(e -> inspectSelected());
        actions.add(inspectBtn);

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadLogs());
        actions.add(refresh);

        header.add(actions, BorderLayout.EAST);
        return header;
    }

    public void loadLogs() {
        statusLabel.setText("Querying audit trail...");
        String query = searchField.getText().trim();
        String filter = "";
        if (!query.isEmpty()) {
            filter = "&or=(action.ilike.*" + query + "*,entity.ilike.*" + query + "*)";
        }

        String finalFilter = filter;
        BackgroundTask.run(() -> {
            return ctx.rest.select("audit_logs", "select=id,action,entity,entity_id,details,created_at,profiles!audit_logs_actor_id_fkey(full_name)&order=created_at.desc&limit=100" + finalFilter);
        }, rows -> {
            model.setRowCount(0);
            if (rows != null && rows.isArray()) {
                for (JsonNode l : rows) {
                    JsonNode p = l.path("profiles");
                    String actor = p.hasNonNull("full_name") ? p.path("full_name").asText() : "System / Headquarters";

                    String date = l.path("created_at").asText();
                    if (date.length() > 19) date = date.substring(0, 19).replace('T', ' ');

                    model.addRow(new Object[]{
                            l.path("id").asText(),
                            date,
                            actor,
                            l.path("action").asText().toUpperCase(),
                            l.path("entity").asText(),
                            l.path("entity_id").asText("\u2014"),
                            l.path("details").toString()
                    });
                }
            }
            statusLabel.setText(model.getRowCount() + " audit event(s) retrieved from database.");
        }, err -> {
            statusLabel.setText("Failed to load audit logs: " + err.getMessage());
        });
    }

    private void inspectSelected() {
        int r = table.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Select an audit row to inspect.", "Notice", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = table.convertRowIndexToModel(r);
        String action = String.valueOf(model.getValueAt(modelRow, 3));
        String details = String.valueOf(model.getValueAt(modelRow, 6));

        JTextArea ta = new JTextArea(details, 8, 30);
        ta.setEditable(false);
        ta.setLineWrap(true);
        JOptionPane.showMessageDialog(this, new JScrollPane(ta), "Audit Details: " + action, JOptionPane.PLAIN_MESSAGE);
    }
}
