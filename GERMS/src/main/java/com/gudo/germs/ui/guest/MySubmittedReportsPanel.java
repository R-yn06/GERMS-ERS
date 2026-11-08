package com.gudo.germs.ui.guest;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Guest "My Submitted Reports" Tracking Panel.
 * Allows members of the public to check verification and response status
 * of their submitted incident reports.
 */
public final class MySubmittedReportsPanel extends JPanel {
    private final AppContext ctx;
    private final Profile user;

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Report ID", "Description / Details", "Location", "Submission Time", "Verification Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JLabel statusLabel = new JLabel(" ");

    public MySubmittedReportsPanel(AppContext ctx, Profile user) {
        super(new BorderLayout(0, 12));
        this.ctx = ctx;
        this.user = user;
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        add(buildHeader(), BorderLayout.NORTH);

        table.setRowHeight(25);
        table.setAutoCreateRowSorter(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        loadReports();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("My Submitted Reports");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel desc = new JLabel("Track the review, verification, and dispatch progress of your submitted emergency reports.");
        desc.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel p = new JPanel(new GridLayout(0, 1, 0, 2));
        p.setOpaque(false);
        p.add(title);
        p.add(desc);
        header.add(p, BorderLayout.WEST);

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadReports());
        header.add(refresh, BorderLayout.EAST);

        return header;
    }

    public void loadReports() {
        statusLabel.setText("Checking verification status of your reports...");
        String filter = "";
        if (user.fullName() != null && !user.fullName().isBlank()) {
            filter = "&reporter_name=eq." + user.fullName();
        }

        String finalFilter = filter;
        BackgroundTask.run(() -> {
            return ctx.rest.select("guest_reports", "select=*&order=created_at.desc" + finalFilter);
        }, rows -> {
            model.setRowCount(0);
            if (rows != null && rows.isArray()) {
                for (JsonNode r : rows) {
                    String time = r.path("created_at").asText();
                    if (time.length() > 19) time = time.substring(0, 19).replace('T', ' ');

                    String st = r.path("status").asText("unverified").toUpperCase();
                    if ("UNVERIFIED".equals(st)) st = "PENDING DISPATCHER VERIFICATION";
                    else if ("VERIFIED".equals(st)) st = "VERIFIED (DISPATCH IN PROGRESS)";
                    else if ("CONVERTED".equals(st)) st = "PUBLISHED OFFICIAL INCIDENT";

                    model.addRow(new Object[]{
                            r.path("id").asText().substring(0, 8).toUpperCase(),
                            r.path("description").asText(),
                            r.path("location_text").asText(""),
                            time,
                            st
                    });
                }
            }
            statusLabel.setText(model.getRowCount() + " report(s) found.");
        }, err -> {
            statusLabel.setText("Could not load reports: " + err.getMessage());
        });
    }
}

