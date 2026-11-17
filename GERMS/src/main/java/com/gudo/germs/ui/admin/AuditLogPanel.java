package com.gudo.germs.ui.admin;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

/**
 * Activity & Audit Log Viewer.
 * Displays append-only chronological history of all incident updates,
 * command decisions, responder deployments, and administrative actions.
 */
public final class AuditLogPanel extends JPanel implements com.gudo.germs.ui.RefreshablePanel {
    private final AppContext ctx;
    private final Profile user;

    private final DefaultTableModel model = new DefaultTableModel(
            new String[] { "ID", "Timestamp", "Actor", "Action", "Entity", "Target ID", "Details JSON" }, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
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
        JPanel header = new JPanel(new BorderLayout(0, 10));
        header.setOpaque(false);

        // Row 1: title + subtitle
        JLabel title = new JLabel("Activity & Audit Logs");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel desc = new JLabel(
                "Tamper-evident, append-only operational audit trail of incident commands, personnel assignments, and system events.");
        desc.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel titles = new JPanel(new GridLayout(0, 1, 0, 2));
        titles.setOpaque(false);
        titles.add(title);
        titles.add(desc);
        header.add(titles, BorderLayout.NORTH);

        // Row 2: search + action buttons
        searchField.addActionListener(e -> loadLogs());
        // Keep the search box at a fixed size so BoxLayout doesn't stretch it.
        searchField.setMaximumSize(searchField.getPreferredSize());

        JButton inspectBtn = new JButton("View Details");
        inspectBtn.addActionListener(e -> inspectSelected());

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadLogs());

        // BoxLayout keeps everything in one row at its natural size (no wrapping).
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));

        actions.add(new JLabel("Search Event:"));
        actions.add(Box.createHorizontalStrut(8));
        actions.add(searchField);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(inspectBtn);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(refresh);
        actions.add(Box.createHorizontalGlue()); // pushes everything to the left

        header.add(actions, BorderLayout.CENTER);
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
            return ctx.rest.select("audit_logs",
                    "select=id,action,entity,entity_id,details,created_at,profiles!audit_logs_actor_id_fkey(full_name)&order=created_at.desc&limit=100"
                            + finalFilter);
        }, rows -> {
            model.setRowCount(0);
            if (rows != null && rows.isArray()) {
                for (JsonNode l : rows) {
                    JsonNode p = l.path("profiles");
                    String actor = p.hasNonNull("full_name") ? p.path("full_name").asText() : "System / Headquarters";

                    String date = l.path("created_at").asText();
                    if (date.length() > 19)
                        date = date.substring(0, 19).replace('T', ' ');

                    model.addRow(new Object[] {
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
            JOptionPane.showMessageDialog(this, "Select an audit row to inspect.", "Notice",
                    JOptionPane.WARNING_MESSAGE);
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

    @Override
    public void refreshData() {
        loadLogs();
    }
}
