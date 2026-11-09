package com.gudo.germs.ui.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Responder "My Assigned Team" View.
 * Displays the responder's current squad, team leader, team contact,
 * deployment status, and fellow squad members.
 */
public final class MyTeamPanel extends JPanel {
    private final AppContext ctx;
    private final Profile user;

    private final JLabel teamNameLabel = new JLabel("Checking team assignment...");
    private final JLabel leaderLabel = new JLabel("Team Leader: \u2014");
    private final JLabel contactLabel = new JLabel("Emergency Contact: \u2014");
    private final JLabel statusLabel = new JLabel("Status: \u2014");

    private final DefaultTableModel memberModel = new DefaultTableModel(
            new String[]{"Squad Member", "Role / Designation", "Contact Email"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable memberTable = new JTable(memberModel);

    public MyTeamPanel(AppContext ctx, Profile user) {
        super(new BorderLayout(0, 14));
        this.ctx = ctx;
        this.user = user;
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        add(buildHeader(), BorderLayout.NORTH);

        memberTable.setRowHeight(25);
        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.setOpaque(false);
        JLabel rosterTitle = new JLabel("Squad Roster & Teammates");
        rosterTitle.setFont(rosterTitle.getFont().deriveFont(Font.BOLD, 14f));
        center.add(rosterTitle, BorderLayout.NORTH);
        center.add(new JScrollPane(memberTable), BorderLayout.CENTER);

        add(center, BorderLayout.CENTER);

        loadTeam();
    }

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);

        teamNameLabel.setFont(teamNameLabel.getFont().deriveFont(Font.BOLD, 22f));

        JPanel infoGrid = new JPanel(new GridLayout(1, 3, 14, 0));
        infoGrid.setOpaque(false);
        infoGrid.add(styledBox("Leader", leaderLabel));
        infoGrid.add(styledBox("Squad Contact", contactLabel));
        infoGrid.add(styledBox("Readiness", statusLabel));

        p.add(teamNameLabel, BorderLayout.NORTH);
        p.add(infoGrid, BorderLayout.CENTER);
        return p;
    }

    private JPanel styledBox(String title, JLabel value) {
        JPanel b = new JPanel(new BorderLayout(0, 4));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(215, 218, 224)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        t.setForeground(GermsColors.SECONDARY_TEXT);
        b.add(t, BorderLayout.NORTH);
        value.setFont(value.getFont().deriveFont(Font.BOLD, 14f));
        b.add(value, BorderLayout.CENTER);
        return b;
    }

    public void loadTeam() {
        BackgroundTask.run(() -> {
            // Find member's team
            JsonNode membership = ctx.rest.select("team_members",
                    "select=team_id,role_title,response_teams(id,name,description,contact_number,status,profiles(full_name))&profile_id=eq." + user.id());
            return membership;
        }, rows -> {
            if (rows != null && rows.isArray() && !rows.isEmpty()) {
                JsonNode m = rows.get(0);
                String teamId = m.path("team_id").asText();
                JsonNode team = m.path("response_teams");

                teamNameLabel.setText(team.path("name").asText("Assigned Response Team"));

                JsonNode leader = team.path("profiles");
                leaderLabel.setText(leader.hasNonNull("full_name") ? leader.path("full_name").asText() : "Command Staff");
                contactLabel.setText(team.path("contact_number").asText("N/A"));
                statusLabel.setText(team.path("status").asText("available").toUpperCase());

                loadTeammates(teamId);
            } else {
                teamNameLabel.setText("Unassigned (Not currently assigned to a response squad)");
                leaderLabel.setText("\u2014");
                contactLabel.setText("\u2014");
                statusLabel.setText("STANDBY");
                memberModel.setRowCount(0);
            }
        }, err -> {
            teamNameLabel.setText("Could not query team: " + err.getMessage());
        });
    }

    private void loadTeammates(String teamId) {
        BackgroundTask.run(() -> {
            return ctx.rest.select("team_members",
                    "select=role_title,profiles(full_name,email)&team_id=eq." + teamId + "&order=role_title.asc");
        }, rows -> {
            memberModel.setRowCount(0);
            if (rows != null && rows.isArray()) {
                for (JsonNode tm : rows) {
                    JsonNode prof = tm.path("profiles");
                    memberModel.addRow(new Object[]{
                            prof.path("full_name").asText("Responder"),
                            tm.path("role_title").asText("Member"),
                            prof.path("email").asText("")
                    });
                }
            }
        }, err -> {});
    }
}

