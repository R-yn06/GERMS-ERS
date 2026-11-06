package com.gudo.germs.ui.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Incident;
import com.gudo.germs.models.Profile;
import com.gudo.germs.models.Role;
import com.gudo.germs.supabase.SupabaseException;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;
import java.util.UUID;

/** Live response monitoring and confirmation of responder assignments. */
public final class DispatchPanel extends JPanel {
    private final AppContext ctx;
    private final Profile user;
    private final JComboBox<Incident> incidents = new JComboBox<>();
    private final DefaultTableModel responseModel = new DefaultTableModel(
            new String[]{"Responder", "Decision", "Responded at", "Responder ID"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable responses = new JTable(responseModel);
    private final JLabel summary = new JLabel("Select an incident to review responses.");
    private final JLabel message = new JLabel(" ");
    private final JButton assign = new JButton("Confirm assignment");
    private final Timer poll = new Timer(5000, e -> refreshResponses());
    private List<Incident> incidentRows = List.of();

    public DispatchPanel(AppContext ctx, Profile user) {
        super(new BorderLayout(0, 12));
        this.ctx = ctx;
        this.user = user;
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        JLabel title = new JLabel("Deployment and Dispatch");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(title, BorderLayout.WEST);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadIncidents());
        header.add(refresh, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        responses.setAutoCreateRowSorter(true);
        responses.removeColumn(responses.getColumnModel().getColumn(3));
        responses.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = responses.getSelectedRow();
                assign.setEnabled(row >= 0 && "ACCEPTED".equals(String.valueOf(
                        responseModel.getValueAt(responses.convertRowIndexToModel(row), 1))));
            }
        });
        JPanel detail = new JPanel(new BorderLayout(0, 12));
        detail.add(summary, BorderLayout.NORTH);
        detail.add(new JScrollPane(responses), BorderLayout.CENTER);
        JPanel controls = new JPanel(new BorderLayout(8, 0));
        controls.setOpaque(false);
        controls.add(incidents, BorderLayout.CENTER);
        controls.add(assign, BorderLayout.EAST);
        assign.addActionListener(e -> confirmAssignment());
        JPanel lower = new JPanel(new BorderLayout());
        lower.setOpaque(false);
        lower.add(controls, BorderLayout.NORTH);
        lower.add(message, BorderLayout.SOUTH);
        detail.add(lower, BorderLayout.SOUTH);
        incidents.addActionListener(e -> refreshResponses());
        JPanel center = new JPanel(new BorderLayout(0, 10));
        JLabel hint = new JLabel("Choose an active incident below; response counts and volunteer decisions refresh every 5 seconds.");
        hint.setForeground(GermsColors.SECONDARY_TEXT);
        center.add(hint, BorderLayout.NORTH);
        center.add(detail, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        poll.setCoalesce(true);
        loadIncidents();
    }

    @Override public void addNotify() {
        super.addNotify();
        if (!poll.isRunning()) poll.start();
        loadIncidents();
    }

    @Override public void removeNotify() {
        poll.stop();
        super.removeNotify();
    }

    private void loadIncidents() {
        BackgroundTask.run(() -> ctx.incidents.visibleTo(false), all -> {
            String selected = selectedIncident() == null ? null : selectedIncident().id();
            incidentRows = all.stream().filter(i -> !i.status().isTerminal() && i.status().key().equals("published")
                    || !i.status().isTerminal() && List.of("acknowledged", "mobilizing", "active_response").contains(i.status().key())).toList();
            incidents.removeAllItems();
            incidentRows.forEach(incidents::addItem);
            if (selected != null) incidentRows.stream().filter(i -> i.id().equals(selected)).findFirst().ifPresent(incidents::setSelectedItem);
            refreshResponses();
        }, err -> showError("Could not load incidents: " + err.getMessage()));
    }

    private Incident selectedIncident() { return (Incident) incidents.getSelectedItem(); }

    private void refreshResponses() {
        Incident incident = selectedIncident();
        if (incident == null || !isDisplayable()) return;
        BackgroundTask.run(() -> {
            JsonNode counts = ctx.rest.rpc("incident_response_summary", java.util.Map.of("p_incident", incident.id()));
            JsonNode rows = ctx.rest.select("incident_responses", "select=responder_id,decision,responded_at,profiles!incident_responses_responder_id_fkey(full_name)&incident_id=eq." + incident.id() + "&order=responded_at.desc");
            return new Object[]{counts, rows};
        }, result -> renderResponses(incident, (JsonNode) result[0], (JsonNode) result[1]),
                err -> showError("Could not refresh dispatch responses: " + err.getMessage()));
    }

    private void renderResponses(Incident incident, JsonNode counts, JsonNode rows) {
        responseModel.setRowCount(0);
        int notified = counts.path("notified").asInt();
        for (JsonNode row : rows) {
            JsonNode profile = row.path("profiles");
            if (profile.isArray() && !profile.isEmpty()) profile = profile.get(0);
            responseModel.addRow(new Object[]{profile.path("full_name").asText("Responder"),
                    row.path("decision").asText().toUpperCase(), row.path("responded_at").asText(), row.path("responder_id").asText()});
        }
        summary.setText("Requested " + counts.path("requested").asInt() + "   ·   Notified " + notified
                + "   ·   Accepted " + counts.path("accepted").asInt() + "   ·   Declined " + counts.path("declined").asInt()
                + "   ·   Pending " + counts.path("pending").asInt() + "   ·   Assigned " + counts.path("assigned").asInt()
                + "   ·   Still needed " + counts.path("remaining").asInt());
        assign.setEnabled(user.role() == Role.ADMIN && responses.getSelectedRow() >= 0);
        message.setText(" ");
    }

    private void confirmAssignment() {
        Incident incident = selectedIncident();
        int selected = responses.getSelectedRow();
        if (incident == null || selected < 0) return;
        int row = responses.convertRowIndexToModel(selected);
        String decision = String.valueOf(responseModel.getValueAt(row, 1));
        if (!"ACCEPTED".equals(decision)) {
            JOptionPane.showMessageDialog(this, "Only responders who accepted can be confirmed here.", "Assignment unavailable", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String responderId = String.valueOf(responseModel.getValueAt(row, 3));
        if (JOptionPane.showConfirmDialog(this, "Confirm this responder for " + incident.title() + "?",
                "Confirm assignment", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        assign.setEnabled(false);
        BackgroundTask.run(() -> ctx.rest.rpc("assign_responder", java.util.Map.of(
                        "p_incident", incident.id(), "p_responder", UUID.fromString(responderId).toString(),
                        "p_role", "Assigned Responder", "p_note", "Confirmed from dispatch response", "p_override", false)),
                ignored -> { message.setText("Assignment confirmed."); refreshResponses(); },
                err -> showError("Assignment failed: " + err.getMessage()));
    }

    private void showError(String text) {
        message.setForeground(GermsColors.forSeverity("high"));
        message.setText(text);
    }
}
