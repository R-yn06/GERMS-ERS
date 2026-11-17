package com.gudo.germs.ui.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.models.Role;
import com.gudo.germs.ui.RefreshablePanel;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;

/** Allows administrators to review public and responder-submitted incident reports. */
public final class PublicReportReviewPanel extends JPanel implements RefreshablePanel {
    private final AppContext ctx;
    private final DefaultTableModel model = new DefaultTableModel(
            new String[] {"ID", "Title", "Reporter", "Category", "Severity", "Submitted", "Status"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JTextArea details = new JTextArea();
    private final JLabel status = new JLabel(" ");
    private final JButton approve = new JButton("Approve as incident draft");
    private final JButton reject = new JButton("Reject report");

    public PublicReportReviewPanel(AppContext ctx, Profile user) {
        super(new BorderLayout(0, 12));
        if (user.role() != Role.ADMIN) throw new IllegalArgumentException("Administrator access is required.");
        this.ctx = ctx;
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JLabel heading = new JLabel("Public Incident Report Review");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 22f));
        JLabel description = new JLabel("Approve preliminary reports into incident drafts, then publish them from Incident Management.");
        description.setForeground(GermsColors.SECONDARY_TEXT);
        JPanel titles = new JPanel(new GridLayout(0, 1, 0, 3));
        titles.setOpaque(false);
        titles.add(heading);
        titles.add(description);

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(event -> loadReports());
        JPanel actions = new JPanel();
        actions.add(approve);
        actions.add(reject);
        actions.add(refresh);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(titles, BorderLayout.CENTER);
        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        table.setRowHeight(26);
        table.removeColumn(table.getColumnModel().getColumn(0));
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) showSelectedDetails();
        });
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        JScrollPane detailPane = new JScrollPane(details);
        detailPane.setBorder(BorderFactory.createTitledBorder("Report details"));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), detailPane);
        split.setResizeWeight(0.65);
        add(split, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        approve.addActionListener(event -> review(true));
        reject.addActionListener(event -> review(false));
        loadReports();
    }

    private void loadReports() {
        status.setText("Loading submitted reports...");
        BackgroundTask.run(() -> ctx.rest.select("guest_reports",
                "select=id,title,reporter_name,category,severity,description,location_text,status,created_at"
                        + "&order=created_at.desc"), rows -> {
            model.setRowCount(0);
            if (rows.isArray()) {
                for (JsonNode report : rows) {
                    model.addRow(new Object[] {
                            report.path("id").asText(),
                            report.path("title").asText("Public incident report"),
                            report.path("reporter_name").asText("Guest"),
                            report.path("category").asText("other").toUpperCase(),
                            report.path("severity").asText("moderate").toUpperCase(),
                            report.path("created_at").asText().replace('T', ' '),
                            report.path("status").asText().toUpperCase()
                    });
                }
            }
            status.setText(model.getRowCount() + " report(s) loaded.");
            showSelectedDetails();
        }, error -> status.setText("Could not load reports: " + error.getMessage()));
    }

    private void showSelectedDetails() {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            details.setText("");
            return;
        }
        String id = String.valueOf(model.getValueAt(table.convertRowIndexToModel(selected), 0));
        BackgroundTask.run(() -> ctx.rest.select("guest_reports",
                "select=description,location_text&id=eq." + id), rows -> {
            if (rows.isArray() && !rows.isEmpty()) {
                JsonNode report = rows.get(0);
                details.setText(report.path("description").asText("")
                        + "\n\nLocation: " + report.path("location_text").asText("Not provided"));
            }
        }, error -> status.setText("Could not load report details: " + error.getMessage()));
    }

    private void review(boolean approveReport) {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            JOptionPane.showMessageDialog(this, "Select a report first.", "No report selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        int row = table.convertRowIndexToModel(selected);
        String id = String.valueOf(model.getValueAt(row, 0));
        String reportStatus = String.valueOf(model.getValueAt(row, 6));
        if (!"UNVERIFIED".equals(reportStatus)) {
            JOptionPane.showMessageDialog(this, "This report has already been reviewed.", "Already reviewed",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String action = approveReport ? "Approve this report and create an incident draft?" : "Reject this report?";
        if (JOptionPane.showConfirmDialog(this, action, "Confirm report review",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;

        BackgroundTask.run(() -> ctx.rest.rpc("review_guest_report",
                java.util.Map.of("p_report", id, "p_approve", approveReport)), result -> {
            loadReports();
            if (approveReport) {
                JOptionPane.showMessageDialog(this,
                        "Report approved and converted to a draft. Open Incident Management to publish it.",
                        "Report approved", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Report rejected.", "Report rejected",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        }, error -> JOptionPane.showMessageDialog(this, "Report review failed: " + error.getMessage(),
                "Review failed", JOptionPane.ERROR_MESSAGE));
    }

    @Override public void refreshData() { loadReports(); }
}
