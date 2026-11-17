package com.gudo.germs.ui.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Incident;
import com.gudo.germs.models.Profile;
import com.gudo.germs.models.Role;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dispatch Center and Centralized Incident Command Interface.
 * Full operational control for emergency commanders: real-time monitoring of responder
 * responses (accepted, declined, pending), conflict-checked assignment confirmation,
 * cancellation, personnel requests, and incident resolution.
 */
public final class DispatchPanel extends JPanel implements com.gudo.germs.ui.RefreshablePanel {
    private final AppContext ctx;
    private final Profile user;

    private final JComboBox<Incident> incidents = new JComboBox<>();
    private final DefaultTableModel responseModel = new DefaultTableModel(
            new String[]{"Responder", "Decision", "Responded at", "Responder ID"}, 0) {
        /** Reports whether the table cell can be edited. */
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable responses = new JTable(responseModel);

    private final DefaultTableModel activeAssignmentsModel = new DefaultTableModel(
            new String[]{"Assignment ID", "Assigned Responder", "Role / Task", "Mission Status", "Assigned At"}, 0) {
        /** Reports whether the table cell can be edited. */
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable activeAssignmentsTable = new JTable(activeAssignmentsModel);

    private final JLabel summary = new JLabel("Select an incident to review responses.");
    private final JLabel message = new JLabel(" ");

    private final JButton assign = new JButton("Deploy Selected Accepted");
    private final JButton cancelAssignment = new JButton("Cancel Assignment");
    private final JButton requestPersonnel = new JButton("Alert All Active Responders");
    private final JButton resolveIncident = new JButton("Resolve / Close Operation");

    private final JCheckBox overrideConflicts = new JCheckBox("Emergency Override (Allow Conflicting Assignments)", false);
    private List<Incident> incidentRows = List.of();

    /** Creates a dispatch panel instance. */
    public DispatchPanel(AppContext ctx, Profile user) {
        super(new BorderLayout(0, 12));
        this.ctx = ctx;
        this.user = user;
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildSplit(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        loadIncidents();
    }

    /** Builds header. */
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Dispatch Center & Incident Command");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel desc = new JLabel("Review responder availability, verify acceptances, confirm deployments, and resolve emergency operations.");
        desc.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel p = new JPanel(new GridLayout(0, 1, 0, 2));
        p.setOpaque(false);
        p.add(title);
        p.add(desc);
        header.add(p, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadIncidents());
        actions.add(refresh);

        header.add(actions, BorderLayout.EAST);
        return header;
    }

    /** Builds split. */
    private JComponent buildSplit() {
        responses.setAutoCreateRowSorter(true);
        responses.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        responses.removeColumn(responses.getColumnModel().getColumn(3)); // Hide ID
        responses.setRowHeight(25);
        responses.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = responses.getSelectedRow();
                assign.setEnabled(hasAcceptedSelection());
            }
        });

        activeAssignmentsTable.setAutoCreateRowSorter(true);
        activeAssignmentsTable.removeColumn(activeAssignmentsTable.getColumnModel().getColumn(0)); // Hide ID
        activeAssignmentsTable.setRowHeight(24);
        activeAssignmentsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                cancelAssignment.setEnabled(activeAssignmentsTable.getSelectedRow() >= 0 && user.role() == Role.ADMIN);
            }
        });

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(summary, BorderLayout.NORTH);
        top.add(new JScrollPane(responses), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);
        JLabel bottomTitle = new JLabel("Confirmed Active Deployments for Selected Incident");
        bottomTitle.setFont(bottomTitle.getFont().deriveFont(Font.BOLD, 13f));
        bottom.add(bottomTitle, BorderLayout.NORTH);
        bottom.add(new JScrollPane(activeAssignmentsTable), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        split.setResizeWeight(0.55);
        split.setBorder(null);

        JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
        centerPanel.setOpaque(false);

        // Incident Selector Bar
        JPanel incBar = new JPanel(new BorderLayout(8, 0));
        incBar.setOpaque(false);
        incBar.add(new JLabel("Active Incident: "), BorderLayout.WEST);
        incidents.addActionListener(e -> refreshResponses());
        incBar.add(incidents, BorderLayout.CENTER);

        centerPanel.add(incBar, BorderLayout.NORTH);
        centerPanel.add(split, BorderLayout.CENTER);

        return centerPanel;
    }

    /** Builds footer. */
    private JPanel buildFooter() {
        JPanel f = new JPanel(new BorderLayout(0, 8));
        f.setOpaque(false);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);

        assign.setBackground(GermsColors.PRIMARY_RED);
        assign.setForeground(Color.WHITE);
        assign.setFont(assign.getFont().deriveFont(Font.BOLD));
        assign.setEnabled(false);
        assign.addActionListener(e -> confirmAssignment());

        cancelAssignment.setEnabled(false);
        cancelAssignment.addActionListener(e -> cancelSelectedAssignment());

        requestPersonnel.addActionListener(e -> showRequestPersonnelDialog());
        resolveIncident.addActionListener(e -> resolveSelectedIncident());

        buttons.add(assign);
        buttons.add(cancelAssignment);
        buttons.add(requestPersonnel);
        buttons.add(resolveIncident);
        buttons.add(overrideConflicts);

        f.add(buttons, BorderLayout.NORTH);
        f.add(message, BorderLayout.SOUTH);
        return f;
    }

    /** Loads active incidents when this panel is added to the component hierarchy. */
    @Override public void addNotify() {
        super.addNotify();
        loadIncidents();
    }

    /** Runs the superclass cleanup when this panel is removed from the component hierarchy. */
    @Override public void removeNotify() {
        super.removeNotify();
    }

    /** Refreshes data. */
    @Override
    public void refreshData() {
        loadIncidents();
    }

    /** Loads incidents. */
    private void loadIncidents() {
        BackgroundTask.run(() -> ctx.incidents.visibleTo(false), all -> {
            Incident selected = selectedIncident();
            String selectedId = selected != null ? selected.id() : null;

            incidentRows = all.stream().filter(i -> i.status().isActive()).toList();
            incidents.removeAllItems();
            for (Incident i : incidentRows) {
                incidents.addItem(i);
                if (i.id().equals(selectedId)) {
                    incidents.setSelectedItem(i);
                }
            }
            refreshResponses();
        }, err -> showError("Could not load incidents: " + err.getMessage()));
    }

    /** Returns the incident currently selected in the incident picker. */
    private Incident selectedIncident() {
        return (Incident) incidents.getSelectedItem();
    }

    /** Refreshes responses. */
    private void refreshResponses() {
        Incident incident = selectedIncident();
        if (incident == null || !isDisplayable()) {
            responseModel.setRowCount(0);
            activeAssignmentsModel.setRowCount(0);
            summary.setText("No active incident selected.");
            return;
        }

        BackgroundTask.run(() -> {
            JsonNode counts = ctx.rest.rpc("incident_response_summary", Map.of("p_incident", incident.id()));
            JsonNode rows = ctx.rest.select("incident_responses",
                    "select=responder_id,decision,responded_at,profiles!incident_responses_responder_id_fkey(full_name)&incident_id=eq." + incident.id() + "&order=responded_at.desc");
            JsonNode asgRows = ctx.rest.select("incident_assignments",
                    "select=id,role_name,status,assigned_at,profiles!incident_assignments_responder_id_fkey(full_name)&incident_id=eq." + incident.id() + "&status=not.in.(completed,released,cancelled)&order=assigned_at.desc");
            return new Object[]{counts, rows, asgRows};
        }, result -> renderResponses(incident, (JsonNode) result[0], (JsonNode) result[1], (JsonNode) result[2]),
                err -> showError("Could not refresh dispatch responses: " + err.getMessage()));
    }

    /** Renders responses. */
    private void renderResponses(Incident incident, JsonNode counts, JsonNode rows, JsonNode asgRows) {
        responseModel.setRowCount(0);
        int notified = counts.path("notified").asInt();
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                JsonNode profile = row.path("profiles");
                if (profile.isArray() && !profile.isEmpty()) profile = profile.get(0);
                responseModel.addRow(new Object[]{
                        profile.path("full_name").asText("Responder"),
                        row.path("decision").asText().toUpperCase(),
                        row.path("responded_at").asText(),
                        row.path("responder_id").asText()
                });
            }
        }

        activeAssignmentsModel.setRowCount(0);
        if (asgRows != null && asgRows.isArray()) {
            for (JsonNode a : asgRows) {
                JsonNode p = a.path("profiles");
                String name = p.hasNonNull("full_name") ? p.path("full_name").asText() : "Assigned Responder";
                String dt = a.path("assigned_at").asText();
                if (dt.length() > 19) dt = dt.substring(0, 19).replace('T', ' ');

                activeAssignmentsModel.addRow(new Object[]{
                        a.path("id").asText(),
                        name,
                        a.path("role_name").asText(),
                        a.path("status").asText().toUpperCase(),
                        dt
                });
            }
        }

        summary.setText(String.format("Personnel Requested: %d  \u00B7  Notified: %d  \u00B7  Accepted: %d  \u00B7  Declined: %d  \u00B7  Pending: %d  \u00B7  Confirmed Assigned: %d  \u00B7  Still Needed: %d",
                counts.path("requested").asInt(), notified, counts.path("accepted").asInt(),
                counts.path("declined").asInt(), counts.path("pending").asInt(),
                counts.path("assigned").asInt(), counts.path("remaining").asInt()));

        assign.setEnabled(user.role() == Role.ADMIN && hasAcceptedSelection());
        message.setText(" ");
    }

    /** Confirms the selected response-team assignment. */
    private void confirmAssignment() {
        Incident incident = selectedIncident();
        int[] selectedRows = responses.getSelectedRows();
        if (incident == null || selectedRows.length == 0) return;
        List<String> acceptedResponderIds = java.util.Arrays.stream(selectedRows)
                .mapToObj(responses::convertRowIndexToModel)
                .filter(row -> "ACCEPTED".equals(String.valueOf(responseModel.getValueAt(row, 1))))
                .map(row -> String.valueOf(responseModel.getValueAt(row, 3)))
                .toList();
        if (acceptedResponderIds.isEmpty()) return;

        boolean override = overrideConflicts.isSelected();

        if (JOptionPane.showConfirmDialog(this,
                "Deploy " + acceptedResponderIds.size() + " accepted responder(s) for " + incident.title() + "?",
                "Confirm Assignment", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;

        assign.setEnabled(false);
        BackgroundTask.run(() -> {
                    for (String responderId : acceptedResponderIds) {
                        ctx.rest.rpc("assign_responder", Map.of(
                                "p_incident", incident.id(),
                                "p_responder", UUID.fromString(responderId).toString(),
                                "p_role", "Assigned Emergency Responder",
                                "p_note", "Confirmed from dispatch center",
                                "p_override", override));
                    }
                    return acceptedResponderIds.size();
                },
                ignored -> {
                    message.setForeground(GermsColors.SUCCESS);
                    message.setText("Deployment confirmed for " + acceptedResponderIds.size() + " responder(s).");
                    refreshResponses();
                },
                err -> showError("Assignment failed: " + err.getMessage()));
    }

    /** Reports whether any selected responder has accepted the deployment. */
    private boolean hasAcceptedSelection() {
        return java.util.Arrays.stream(responses.getSelectedRows())
                .map(responses::convertRowIndexToModel)
                .anyMatch(row -> "ACCEPTED".equals(String.valueOf(responseModel.getValueAt(row, 1))));
    }

    /** Cancels the selected assignment after prompting for a reason. */
    private void cancelSelectedAssignment() {
        int r = activeAssignmentsTable.getSelectedRow();
        if (r < 0) return;
        int row = activeAssignmentsTable.convertRowIndexToModel(r);
        String asgId = String.valueOf(activeAssignmentsModel.getValueAt(row, 0));
        String name = String.valueOf(activeAssignmentsModel.getValueAt(row, 1));

        String reason = JOptionPane.showInputDialog(this, "Reason for canceling deployment of " + name + ":",
                "Cancel Deployment", JOptionPane.PLAIN_MESSAGE);
        if (reason == null) return;

        BackgroundTask.run(() -> {
            ctx.rest.rpc("update_assignment_status", Map.of(
                    "p_assignment", UUID.fromString(asgId),
                    "p_to", "cancelled",
                    "p_note", reason
            ));
            return Boolean.TRUE;
        }, ok -> {
            message.setForeground(GermsColors.SUCCESS);
            message.setText("Deployment cancelled.");
            refreshResponses();
        }, err -> showError("Cancel failed: " + err.getMessage()));
    }

    /** Displays request personnel dialog. */
    private void showRequestPersonnelDialog() {
        Incident inc = selectedIncident();
        if (inc == null) return;

        JTextField instructions = new JTextField("Urgent response deployment request. Please report to staging.");
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.add(new JLabel("All active responders who opted in will be alerted. Accepted responders can then be selected for deployment."), BorderLayout.NORTH);
        p.add(instructions, BorderLayout.SOUTH);
        p.setPreferredSize(new Dimension(520, 90));

        int res = JOptionPane.showConfirmDialog(this, p, "Alert Responders for " + inc.incidentNo(),
                JOptionPane.OK_CANCEL_OPTION);
        if (res != JOptionPane.OK_OPTION) return;

        BackgroundTask.run(() -> {
            JsonNode result = ctx.rest.rpc("request_all_active_responder_deployments", Map.of(
                    "p_incident", UUID.fromString(inc.id()),
                    "p_instructions", instructions.getText().trim()
            ));
            return result.asInt();
        }, count -> {
            message.setForeground(GermsColors.SUCCESS);
            message.setText("Alert delivered to " + count + " opted-in active responder(s).");
            refreshResponses();
        }, err -> showError("Request failed: " + err.getMessage()));
    }

    /** Resolves the selected incident. */
    private void resolveSelectedIncident() {
        Incident inc = selectedIncident();
        if (inc == null) return;

        String summary = JOptionPane.showInputDialog(this, "Enter final mission resolution / closure summary:",
                "Resolve Incident Operation", JOptionPane.PLAIN_MESSAGE);
        if (summary == null || summary.isBlank()) return;

        BackgroundTask.run(() -> {
            ctx.incidents.advance(inc.id(), user.fullName(), summary.trim());
            return Boolean.TRUE;
        }, ok -> {
            message.setText("Incident resolved.");
            loadIncidents();
        }, err -> showError("Resolution failed: " + err.getMessage()));
    }

    /** Displays error. */
    private void showError(String text) {
        message.setForeground(GermsColors.CRITICAL);
        message.setText(text);
    }

}
