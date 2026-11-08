package com.gudo.germs.ui.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.ui.components.StatCard;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Operational Analytics and Incident Reporting.
 * Computes live operational metrics from Supabase, summarizes incidents by severity and category,
 * monitors responder readiness and shelter capacity, and exports operational CSV reports.
 */
public final class ReportsAnalyticsPanel extends JPanel {
    private final AppContext ctx;
    private final Profile user;

    private final StatCard totalCard = new StatCard("Total Incidents", "\u2026", GermsColors.INFO);
    private final StatCard activeCard = new StatCard("Active Operations", "\u2026", GermsColors.CRITICAL);
    private final StatCard closedCard = new StatCard("Resolved / Closed", "\u2026", GermsColors.SUCCESS);
    private final StatCard respondersCard = new StatCard("Active Responders", "\u2026", new Color(0, 114, 206));

    private final DefaultTableModel breakdownModel = new DefaultTableModel(
            new String[]{"Incident #", "Title", "Category", "Severity", "Status", "Reported At", "Location"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable breakdownTable = new JTable(breakdownModel);

    private final JComboBox<String> severityFilter = new JComboBox<>(new String[]{"All Severities", "critical", "high", "moderate", "low"});
    private final JComboBox<String> categoryFilter = new JComboBox<>(new String[]{"All Categories", "Medical Emergency", "Vehicular Accident", "Fire", "Flood", "Search and Rescue", "Other"});
    private final JLabel analyticsSummary = new JLabel(" ");

    private JsonNode cachedIncidents = null;

    public ReportsAnalyticsPanel(AppContext ctx, Profile user) {
        super(new BorderLayout(0, 14));
        this.ctx = ctx;
        this.user = user;
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenterArea(), BorderLayout.CENTER);
        add(analyticsSummary, BorderLayout.SOUTH);

        loadAnalytics();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Reports & Operational Analytics");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel desc = new JLabel("Real-time operational reporting, incident breakdown, casualty metrics, and formal data exports.");
        desc.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel p = new JPanel(new GridLayout(0, 1, 0, 2));
        p.setOpaque(false);
        p.add(title);
        p.add(desc);
        header.add(p, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        actions.add(new JLabel("Severity:"));
        severityFilter.addActionListener(e -> filterData());
        actions.add(severityFilter);

        actions.add(new JLabel("Category:"));
        categoryFilter.addActionListener(e -> filterData());
        actions.add(categoryFilter);

        JButton exportCsv = new JButton("Export CSV Report");
        exportCsv.setBackground(GermsColors.PRIMARY_RED);
        exportCsv.setForeground(Color.WHITE);
        exportCsv.addActionListener(e -> exportCsv());
        actions.add(exportCsv);

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadAnalytics());
        actions.add(refresh);

        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JComponent buildCenterArea() {
        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);

        // 4 Stat Cards Row
        JPanel statsRow = new JPanel(new GridLayout(1, 4, 14, 0));
        statsRow.setOpaque(false);
        statsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        statsRow.add(totalCard);
        statsRow.add(activeCard);
        statsRow.add(closedCard);
        statsRow.add(respondersCard);
        center.add(statsRow, BorderLayout.NORTH);

        // Breakdown Table
        breakdownTable.setRowHeight(25);
        breakdownTable.setAutoCreateRowSorter(true);
        center.add(new JScrollPane(breakdownTable), BorderLayout.CENTER);

        return center;
    }

    public void loadAnalytics() {
        analyticsSummary.setText("Computing operational metrics from database...");
        BackgroundTask.run(() -> {
            JsonNode incs = ctx.rest.select("incidents", "select=id,incident_no,title,category,severity,status,reported_at,location_text,closure_summary&order=reported_at.desc");
            JsonNode resps = ctx.rest.select("profiles", "select=id,is_active&role=eq.responder&is_active=eq.true");
            return new Object[]{incs, resps};
        }, res -> {
            cachedIncidents = (JsonNode) res[0];
            JsonNode resps = (JsonNode) res[1];

            int total = 0;
            int active = 0;
            int closed = 0;

            if (cachedIncidents != null && cachedIncidents.isArray()) {
                total = cachedIncidents.size();
                for (JsonNode i : cachedIncidents) {
                    String st = i.path("status").asText();
                    if ("closed".equalsIgnoreCase(st) || "archived".equalsIgnoreCase(st)) {
                        closed++;
                    } else if (!"draft".equalsIgnoreCase(st)) {
                        active++;
                    }
                }
            }

            int responders = (resps != null && resps.isArray()) ? resps.size() : 0;

            totalCard.setValue(String.valueOf(total));
            activeCard.setValue(String.valueOf(active));
            closedCard.setValue(String.valueOf(closed));
            respondersCard.setValue(String.valueOf(responders));

            filterData();
            analyticsSummary.setText(String.format("Analytics synchronized as of %s. Active deployment rate: %.1f%%",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    total > 0 ? (active * 100.0 / total) : 0.0));
        }, err -> {
            analyticsSummary.setText("Analytics aggregation error: " + err.getMessage());
        });
    }

    private void filterData() {
        if (cachedIncidents == null || !cachedIncidents.isArray()) return;

        String sevSel = (String) severityFilter.getSelectedItem();
        String catSel = (String) categoryFilter.getSelectedItem();

        breakdownModel.setRowCount(0);
        for (JsonNode i : cachedIncidents) {
            String sev = i.path("severity").asText();
            String cat = i.path("category").asText().replace('_', ' ');

            if (sevSel != null && !"All Severities".equalsIgnoreCase(sevSel) && !sevSel.equalsIgnoreCase(sev)) {
                continue;
            }
            if (catSel != null && !"All Categories".equalsIgnoreCase(catSel) && !cat.toLowerCase().contains(catSel.toLowerCase())) {
                continue;
            }

            breakdownModel.addRow(new Object[]{
                    i.path("incident_no").asText("GERMS"),
                    i.path("title").asText(),
                    cat.toUpperCase(),
                    sev.toUpperCase(),
                    i.path("status").asText().toUpperCase(),
                    i.path("reported_at").asText("").replace('T', ' '),
                    i.path("location_text").asText("")
            });
        }
    }

    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("GERMS_Operational_Report_" + System.currentTimeMillis() + ".csv"));
        int res = chooser.showSaveDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return;

        File f = chooser.getSelectedFile();
        try (FileWriter w = new FileWriter(f, StandardCharsets.UTF_8)) {
            w.write("Incident Number,Title,Category,Severity,Status,Reported At,Location\n");
            for (int r = 0; r < breakdownModel.getRowCount(); r++) {
                StringBuilder line = new StringBuilder();
                for (int c = 0; c < breakdownModel.getColumnCount(); c++) {
                    String val = String.valueOf(breakdownModel.getValueAt(r, c)).replace("\"", "\"\"");
                    line.append("\"").append(val).append("\"");
                    if (c < breakdownModel.getColumnCount() - 1) line.append(",");
                }
                w.write(line.toString() + "\n");
            }
            JOptionPane.showMessageDialog(this, "Operational CSV report exported successfully to:\n" + f.getAbsolutePath(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Export failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}

