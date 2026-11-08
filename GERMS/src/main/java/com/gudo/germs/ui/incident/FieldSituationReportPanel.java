package com.gudo.germs.ui.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Incident;
import com.gudo.germs.models.Profile;
import com.gudo.germs.models.Role;
import com.gudo.germs.ui.components.LocationPickerDialog;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * Field Situation Reporting (SITREP) Module.
 * Deployed responders submit live field updates, casualty estimates,
 * and urgent resource/medical needs. Administrators review sitreps and deliver feedback.
 */
public final class FieldSituationReportPanel extends JPanel {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final AppContext ctx;
    private final Profile user;
    private final boolean isAdmin;

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Incident", "Timestamp", "Reporter", "Conditions", "Affected", "Casualties", "Urgent"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);

    private final JTextArea descriptionArea = new JTextArea();
    private final JTextArea medicalNeedsArea = new JTextArea();
    private final JTextArea resourceNeedsArea = new JTextArea();
    private final JTextArea adminFeedbackArea = new JTextArea();
    private final JLabel headerLabel = new JLabel("Select a field situation report to inspect details.");
    private final JLabel statusMessage = new JLabel(" ");

    public FieldSituationReportPanel(AppContext ctx, Profile user) {
        super(new BorderLayout(0, 12));
        this.ctx = ctx;
        this.user = user;
        this.isAdmin = user.role() == Role.ADMIN;
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildMainSplit(), BorderLayout.CENTER);
        add(statusMessage, BorderLayout.SOUTH);

        loadReports();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Field Situation Reports (SITREP)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel desc = new JLabel(isAdmin
                ? "Review real-time intelligence submitted by deployed response teams, assess casualties, and provide feedback."
                : "Submit operational field assessments, casualty counts, urgent medical requests, and situation updates.");
        desc.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel p = new JPanel(new GridLayout(0, 1, 0, 2));
        p.setOpaque(false);
        p.add(title);
        p.add(desc);
        header.add(p, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        JButton submitBtn = new JButton("+ Submit Field SITREP");
        submitBtn.setBackground(GermsColors.PRIMARY_RED);
        submitBtn.setForeground(Color.WHITE);
        submitBtn.setFont(submitBtn.getFont().deriveFont(Font.BOLD));
        submitBtn.addActionListener(e -> showSubmitSitrepDialog());

        JButton feedbackBtn = new JButton("Add Command Feedback");
        feedbackBtn.addActionListener(e -> showAddFeedbackDialog());
        if (!isAdmin) feedbackBtn.setVisible(false);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> loadReports());

        actions.add(submitBtn);
        if (isAdmin) actions.add(feedbackBtn);
        actions.add(refreshBtn);

        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JComponent buildMainSplit() {
        table.setRowHeight(25);
        table.removeColumn(table.getColumnModel().getColumn(0)); // Hide ID
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showDetail();
            }
        });

        JPanel detailPanel = new JPanel();
        detailPanel.setLayout(new BoxLayout(detailPanel, BoxLayout.Y_AXIS));
        detailPanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));

        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);

        medicalNeedsArea.setEditable(false);
        medicalNeedsArea.setLineWrap(true);
        resourceNeedsArea.setEditable(false);
        resourceNeedsArea.setLineWrap(true);
        adminFeedbackArea.setEditable(false);
        adminFeedbackArea.setLineWrap(true);

        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 15f));
        headerLabel.setAlignmentX(LEFT_ALIGNMENT);

        detailPanel.add(headerLabel);
        detailPanel.add(Box.createVerticalStrut(10));
        detailPanel.add(titledBlock("Situation Description:", descriptionArea, 100));
        detailPanel.add(Box.createVerticalStrut(8));
        detailPanel.add(titledBlock("Medical Assistance Requirements:", medicalNeedsArea, 60));
        detailPanel.add(Box.createVerticalStrut(8));
        detailPanel.add(titledBlock("Resource & Equipment Needed:", resourceNeedsArea, 60));
        detailPanel.add(Box.createVerticalStrut(8));
        detailPanel.add(titledBlock("Command Center Feedback:", adminFeedbackArea, 60));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(table), new JScrollPane(detailPanel));
        split.setResizeWeight(0.58);
        split.setBorder(null);
        return split;
    }

    private JPanel titledBlock(String title, JTextArea area, int height) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        JLabel l = new JLabel(title);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 12f));
        l.setForeground(GermsColors.SECONDARY_TEXT);
        p.add(l, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(area);
        sp.setPreferredSize(new Dimension(300, height));
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private JsonNode cachedRows = null;

    public void loadReports() {
        statusMessage.setText("Loading situation reports...");
        BackgroundTask.run(() -> {
            return ctx.rest.select("field_situation_reports",
                    "select=id,situation_description,field_conditions,affected_individuals,casualties_count,medical_needs,resource_needs,additional_observations,urgent,admin_feedback,created_at,incidents(incident_no,title),profiles!field_situation_reports_reporter_id_fkey(full_name)&order=created_at.desc");
        }, rows -> {
            cachedRows = rows;
            model.setRowCount(0);
            if (rows != null && rows.isArray()) {
                for (JsonNode r : rows) {
                    JsonNode inc = r.path("incidents");
                    String incName = inc.hasNonNull("incident_no") ? (inc.path("incident_no").asText() + " " + inc.path("title").asText()) : "Incident";
                    JsonNode p = r.path("profiles");
                    String reporter = p.hasNonNull("full_name") ? p.path("full_name").asText() : "Responder";

                    String dateStr = r.path("created_at").asText();
                    try {
                        dateStr = TIME_FMT.format(Instant.parse(dateStr));
                    } catch (Exception ignored) {}

                    boolean urgent = r.path("urgent").asBoolean();

                    model.addRow(new Object[]{
                            r.path("id").asText(),
                            incName,
                            dateStr,
                            reporter,
                            r.path("field_conditions").asText("Normal"),
                            r.path("affected_individuals").asInt(),
                            r.path("casualties_count").asInt(),
                            urgent ? "YES (URGENT)" : "No"
                    });
                }
            }
            statusMessage.setText(model.getRowCount() + " situation report(s) retrieved.");
            if (table.getRowCount() > 0 && table.getSelectedRow() < 0) {
                table.setRowSelectionInterval(0, 0);
            }
        }, err -> statusMessage.setText("Failed to load situation reports: " + err.getMessage()));
    }

    private void showDetail() {
        int r = table.getSelectedRow();
        if (r < 0 || cachedRows == null) {
            headerLabel.setText("Select a field situation report to inspect details.");
            descriptionArea.setText("");
            medicalNeedsArea.setText("");
            resourceNeedsArea.setText("");
            adminFeedbackArea.setText("");
            return;
        }

        int modelRow = table.convertRowIndexToModel(r);
        String id = String.valueOf(model.getValueAt(modelRow, 0));

        JsonNode rowData = null;
        for (JsonNode n : cachedRows) {
            if (id.equals(n.path("id").asText())) {
                rowData = n;
                break;
            }
        }
        if (rowData == null) return;

        headerLabel.setText(String.valueOf(model.getValueAt(modelRow, 1)) + " \u2014 " + String.valueOf(model.getValueAt(modelRow, 3)));
        descriptionArea.setText(rowData.path("situation_description").asText(""));
        medicalNeedsArea.setText(rowData.path("medical_needs").asText("None indicated"));
        resourceNeedsArea.setText(rowData.path("resource_needs").asText("None indicated"));
        adminFeedbackArea.setText(rowData.path("admin_feedback").asText("Pending command review"));
    }

    private void showSubmitSitrepDialog() {
        BackgroundTask.run(() -> ctx.incidents.visibleTo(false), incidents -> {
            List<Incident> active = incidents.stream().filter(i -> i.status().isActive()).toList();
            if (active.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No active incidents currently available for SITREP reporting.", "Notice", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            JComboBox<Incident> incBox = new JComboBox<>(active.toArray(Incident[]::new));
            JTextArea descField = new JTextArea(4, 25);
            descField.setLineWrap(true);
            JTextField conditionsField = new JTextField("Heavy rain, road partially accessible");
            JSpinner affectedSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100000, 1));
            JSpinner casualtiesSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
            JTextField medicalField = new JTextField("Trauma kits, oxygen, stretchers");
            JTextField resourceField = new JTextField("Ambulance, rescue boat");
            JCheckBox urgentBox = new JCheckBox("Mark as URGENT (immediate command notification)", false);
            LocationPickerDialog.Selection[] fieldLocation = new LocationPickerDialog.Selection[1];
            JLabel fieldLocationText = new JLabel("No field location selected (optional)");
            JButton chooseLocation = new JButton("Pin / Search on Map");
            chooseLocation.addActionListener(event -> {
                LocationPickerDialog.Selection initial = fieldLocation[0];
                if (initial == null && incBox.getSelectedItem() instanceof Incident selectedIncident
                        && selectedIncident.latitude() != null && selectedIncident.longitude() != null) {
                    initial = new LocationPickerDialog.Selection(selectedIncident.latitude(),
                            selectedIncident.longitude(), selectedIncident.location(), "manual", null);
                }
                LocationPickerDialog.Selection picked = LocationPickerDialog.showDialog(
                        SwingUtilities.getWindowAncestor(this), "Select Field Report Location", initial);
                if (picked != null) {
                    fieldLocation[0] = picked;
                    fieldLocationText.setText(String.format(java.util.Locale.ROOT, "%s (%.5f, %.5f)",
                            picked.label(), picked.latitude(), picked.longitude()));
                }
            });

            JPanel p = new JPanel(new GridLayout(0, 2, 6, 6));
            p.add(new JLabel("Incident:")); p.add(incBox);
            p.add(new JLabel("Field Location:"));
            JPanel locationPicker = new JPanel(new BorderLayout(8, 0));
            locationPicker.add(chooseLocation, BorderLayout.WEST);
            locationPicker.add(fieldLocationText, BorderLayout.CENTER);
            p.add(locationPicker);
            p.add(new JLabel("Situation Summary:")); p.add(new JScrollPane(descField));
            p.add(new JLabel("Field Conditions:")); p.add(conditionsField);
            p.add(new JLabel("Affected Individuals:")); p.add(affectedSpinner);
            p.add(new JLabel("Casualties Count:")); p.add(casualtiesSpinner);
            p.add(new JLabel("Medical Needs:")); p.add(medicalField);
            p.add(new JLabel("Resource Needs:")); p.add(resourceField);
            p.add(new JLabel("Priority:")); p.add(urgentBox);

            int choice = JOptionPane.showConfirmDialog(this, p, "Submit Field Situation Report", JOptionPane.OK_CANCEL_OPTION);
            if (choice != JOptionPane.OK_OPTION) return;

            String desc = descField.getText().trim();
            if (desc.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Situation summary cannot be blank.", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            Incident inc = (Incident) incBox.getSelectedItem();
            LocationPickerDialog.Selection location = fieldLocation[0];

            BackgroundTask.run(() -> {
                Map<String, Object> args = new HashMap<>();
                args.put("p_incident", UUID.fromString(inc.id()));
                args.put("p_description", desc);
                args.put("p_conditions", conditionsField.getText().trim());
                args.put("p_affected", affectedSpinner.getValue());
                args.put("p_casualties", casualtiesSpinner.getValue());
                args.put("p_medical", medicalField.getText().trim());
                args.put("p_resources", resourceField.getText().trim());
                args.put("p_observations", "");
                args.put("p_urgent", urgentBox.isSelected());
                if (location != null) {
                    args.put("p_lat", location.latitude());
                    args.put("p_lon", location.longitude());
                }
                ctx.rest.rpc("submit_field_situation_report", args);
                return Boolean.TRUE;
            }, ok -> {
                loadReports();
                JOptionPane.showMessageDialog(this, "Field SITREP submitted successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
            }, err -> JOptionPane.showMessageDialog(this, "Submission failed: " + err.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
        }, err -> JOptionPane.showMessageDialog(this, "Failed to load incidents: " + err.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
    }

    private void showAddFeedbackDialog() {
        int r = table.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Select a report to provide feedback.", "Notice", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = table.convertRowIndexToModel(r);
        String reportId = String.valueOf(model.getValueAt(modelRow, 0));

        String feedback = JOptionPane.showInputDialog(this, "Enter Command Center directive / feedback for the field team:",
                "Command SITREP Feedback", JOptionPane.PLAIN_MESSAGE);
        if (feedback == null || feedback.isBlank()) return;

        BackgroundTask.run(() -> {
            ctx.rest.insertMinimal("field_situation_reports", Map.of(
                    "id", reportId,
                    "admin_feedback", feedback.trim(),
                    "reviewed_by", user.id().toString()
            ));
            return Boolean.TRUE;
        }, ok -> {
            loadReports();
            JOptionPane.showMessageDialog(this, "Feedback recorded.", "Success", JOptionPane.INFORMATION_MESSAGE);
        }, err -> JOptionPane.showMessageDialog(this, "Failed: " + err.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
    }
}
