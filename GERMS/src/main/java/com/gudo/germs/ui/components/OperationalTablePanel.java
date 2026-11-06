package com.gudo.germs.ui.components;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;

/**
 * Shared read-only operational view for modules whose data is already exposed by
 * the Supabase schema. It deliberately shows backend errors instead of masking
 * an unavailable table or an authorization failure.
 */
public final class OperationalTablePanel extends JPanel {
    public record Column(String label, String field) {}

    private final AppContext ctx;
    private final String table;
    private final String query;
    private final List<Column> columns;
    private final JLabel state = new JLabel("Loading...", SwingConstants.LEFT);
    private final DefaultTableModel model;

    public OperationalTablePanel(AppContext ctx, String title, String description,
                                 String table, String query, List<Column> columns) {
        super(new BorderLayout(0, 12));
        this.ctx = ctx;
        this.table = table;
        this.query = query;
        this.columns = columns;
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        JPanel header = new JPanel(new BorderLayout(12, 4));
        header.setOpaque(false);
        JPanel copy = new JPanel(new BorderLayout(0, 4));
        copy.setOpaque(false);
        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 22f));
        copy.add(heading, BorderLayout.NORTH);
        JLabel details = new JLabel(description);
        details.setForeground(GermsColors.SECONDARY_TEXT);
        copy.add(details, BorderLayout.CENTER);
        header.add(copy, BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> load());
        header.add(refresh, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        model = new DefaultTableModel(columns.stream().map(Column::label).toArray(), 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable tableView = new JTable(model);
        tableView.setAutoCreateRowSorter(true);
        add(new JScrollPane(tableView), BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        footer.setOpaque(false);
        footer.add(state);
        add(footer, BorderLayout.SOUTH);
    }

    @Override public void addNotify() {
        super.addNotify();
        load();
    }

    private void load() {
        state.setText("Loading " + table + "...");
        BackgroundTask.run(() -> ctx.rest.select(table, query), this::showRows, error -> {
            model.setRowCount(0);
            state.setForeground(GermsColors.CRITICAL);
            state.setText("Could not load " + table + ": " + error.getMessage());
        });
    }

    private void showRows(JsonNode rows) {
        model.setRowCount(0);
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                Object[] values = columns.stream().map(c -> display(row, c.field())).toArray();
                model.addRow(values);
            }
        }
        state.setForeground(GermsColors.SECONDARY_TEXT);
        state.setText(model.getRowCount() + " record(s) loaded from Supabase.");
    }

    private static String display(JsonNode row, String field) {
        JsonNode value = row.path(field);
        if (value.isMissingNode() || value.isNull()) return "";
        return value.isValueNode() ? value.asText() : value.toString();
    }
}
