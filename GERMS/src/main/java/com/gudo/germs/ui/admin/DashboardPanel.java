package com.gudo.germs.ui.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.ui.components.StatCard;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Font;
import java.awt.GridLayout;

/**
 * Phase 1 dashboard: proves the authenticated, RLS-filtered round trip to Supabase with real counts.
 */
public final class DashboardPanel extends JPanel {
    private final AppContext ctx;
    private final StatCard visible = new StatCard("Incidents visible to you", "\u2026", GermsColors.INFO);
    private final StatCard active = new StatCard("Active incidents", "\u2026", GermsColors.CRITICAL);
    private final JLabel status = new JLabel(" ");

    public DashboardPanel(AppContext ctx, Profile user) {
        this.ctx = ctx;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        JLabel title = new JLabel("Welcome, " + user.fullName());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setAlignmentX(LEFT_ALIGNMENT);
        add(title);
        add(Box.createVerticalStrut(16));

        JPanel row = new JPanel(new GridLayout(1, 2, 16, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 100));
        row.add(visible);
        row.add(active);
        add(row);
        add(Box.createVerticalStrut(12));

        JButton refresh = new JButton("Refresh");
        refresh.setAlignmentX(LEFT_ALIGNMENT);
        refresh.addActionListener(e -> load());
        add(refresh);
        add(Box.createVerticalStrut(8));
        status.setAlignmentX(LEFT_ALIGNMENT);
        add(status);
        add(Box.createVerticalStrut(16));

        JLabel note = new JLabel("<html>Live updates: <b>not yet connected</b> (Realtime client is the next build step). "
                + "Counts above are real and refreshed on demand.</html>");
        note.setForeground(GermsColors.SECONDARY_TEXT);
        note.setAlignmentX(LEFT_ALIGNMENT);
        add(note);
        add(Box.createVerticalGlue());
        load();
    }

    private void load() {
        status.setText("Loading\u2026");
        status.setForeground(GermsColors.SECONDARY_TEXT);
        BackgroundTask.run(() -> ctx.rest.select("incidents", "select=status"), rows -> {
            int total = rows.size();
            int act = 0;
            for (JsonNode r : rows) {
                String s = r.path("status").asText();
                if (s.equals("published") || s.equals("acknowledged") || s.equals("mobilizing")
                        || s.equals("active_response") || s.equals("stabilized")) act++;
            }
            visible.setValue(String.valueOf(total));
            active.setValue(String.valueOf(act));
            status.setText(" ");
        }, err -> {
            visible.setValue("\u2014");
            active.setValue("\u2014");
            status.setText("Could not load incidents: " + err.getMessage()
                    + " (have migrations 001\u2013004 been applied?)");
            status.setForeground(GermsColors.CRITICAL);
        });
    }
}
