package com.gudo.germs.ui.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Incident;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * User & Responder Management Interface.
 * Supports viewing all accounts, promoting Guests to Responders with classification assignment,
 * managing availability, creating accounts, and requesting deployments.
 */
public final class UserManagementPanel extends JPanel {
    private final AppContext ctx;
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Name", "Email", "Role", "Classification", "Active", "Availability", "Deployment"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JLabel state = new JLabel(" ");
    private final JComboBox<String> filterBox = new JComboBox<>(new String[]{"All Accounts", "Responders Only", "Guests / Users", "Administrators"});

    public UserManagementPanel(AppContext ctx) {
        super(new BorderLayout(0, 12));
        this.ctx = ctx;
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        JLabel title = new JLabel("User & Responder Management");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel description = new JLabel("Manage personnel records, promote guests to responders, configure classifications, and monitor availability.");
        description.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel copy = new JPanel(new BorderLayout(0, 4));
        copy.setOpaque(false);
        copy.add(title, BorderLayout.NORTH);
        copy.add(description, BorderLayout.SOUTH);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        actions.setOpaque(false);

        actions.add(new JLabel("Filter:"));
        filterBox.addActionListener(e -> load());
        actions.add(filterBox);

        JButton promote = new JButton("Promote to Responder");
        promote.setBackground(GermsColors.PRIMARY_RED);
        promote.setForeground(Color.WHITE);
        promote.setFont(promote.getFont().deriveFont(Font.BOLD));
        promote.addActionListener(e -> promoteSelected());
        actions.add(promote);

        JButton availability = new JButton("Set Availability");
        availability.addActionListener(e -> setAvailability());
        actions.add(availability);

        JButton request = new JButton("Request Deployment");
        request.addActionListener(e -> requestDeployment());
        actions.add(request);

        JButton add = new JButton("+ Add Account");
        add.addActionListener(e -> addAccount());
        actions.add(add);

        JButton edit = new JButton("Edit");
        edit.addActionListener(e -> editSelected());
        actions.add(edit);

        JButton deactivate = new JButton("Deactivate");
        deactivate.addActionListener(e -> deactivateSelected());
        actions.add(deactivate);

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> load());
        actions.add(refresh);

        JPanel header = new JPanel(new BorderLayout(12, 6));
        header.setOpaque(false);
        header.add(copy, BorderLayout.NORTH);
        header.add(actions, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        table.removeColumn(table.getColumnModel().getColumn(0)); // Hide ID
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(26);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(state, BorderLayout.SOUTH);

        load();
    }

    private void load() {
        String sel = (String) filterBox.getSelectedItem();
        String filter = "";
        if ("Responders Only".equals(sel)) filter = "&role=eq.responder";
        else if ("Guests / Users".equals(sel)) filter = "&role=eq.guest";
        else if ("Administrators".equals(sel)) filter = "&role=eq.admin";

        String finalFilter = filter;
        state.setText("Loading accounts from Supabase...");
        BackgroundTask.run(() -> ctx.rest.select("profiles",
                "select=id,full_name,email,role,is_active,responder_availability(status),incident_assignments!incident_assignments_responder_id_fkey(status,incident_id),profile_classifications!profile_classifications_profile_id_fkey(responder_classifications(name,code))&order=full_name.asc" + finalFilter),
                this::render, e -> state.setText("Could not load users: " + e.getMessage()));
    }

    private void render(JsonNode rows) {
        model.setRowCount(0);
        if (rows != null && rows.isArray()) {
            for (JsonNode n : rows) {
                JsonNode availability = n.path("responder_availability");
                if (availability.isArray() && !availability.isEmpty()) availability = availability.get(0);

                JsonNode assignments = n.path("incident_assignments");
                boolean deployed = false;
                if (assignments.isArray()) {
                    for (JsonNode a : assignments) {
                        String st = a.path("status").asText();
                        if (!"completed".equals(st) && !"released".equals(st) && !"cancelled".equals(st)) {
                            deployed = true;
                            break;
                        }
                    }
                }

                String classificationName = "\u2014";
                JsonNode clsNode = n.path("profile_classifications");
                if (clsNode.isArray() && !clsNode.isEmpty()) {
                    JsonNode rc = clsNode.get(0).path("responder_classifications");
                    if (!rc.isMissingNode() && rc.hasNonNull("name")) {
                        classificationName = rc.get("name").asText();
                    }
                }

                model.addRow(new Object[]{
                        n.path("id").asText(),
                        n.path("full_name").asText(),
                        n.path("email").asText(),
                        n.path("role").asText().toUpperCase(Locale.ROOT),
                        classificationName,
                        n.path("is_active").asBoolean() ? "Active" : "Inactive",
                        availability.path("status").asText("available").toUpperCase(Locale.ROOT),
                        deployed ? "DEPLOYED" : "AVAILABLE"
                });
            }
        }
        state.setForeground(GermsColors.SECONDARY_TEXT);
        state.setText(model.getRowCount() + " account(s) loaded.");
    }

    private String selectedId() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an account first.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return String.valueOf(model.getValueAt(table.convertRowIndexToModel(row), 0));
    }

    private void promoteSelected() {
        String id = selectedId();
        if (id == null) return;
        int row = table.convertRowIndexToModel(table.getSelectedRow());
        String name = String.valueOf(model.getValueAt(row, 1));
        String currentRole = String.valueOf(model.getValueAt(row, 3));

        if ("RESPONDER".equalsIgnoreCase(currentRole)) {
            JOptionPane.showMessageDialog(this, name + " is already a Responder.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String[] classifications = {
                "DMS_SAR - Search and Rescue",
                "DMS_FLOOD - Flood and Landslide Response",
                "DMS_FIRE - Typhoon and Fire Response",
                "DMS_ASSESS - Disaster Assessment",
                "DMS_EVAC - Evacuation Operations",
                "DMS_RELIEF - Relief Logistics",
                "SAF_ERU - Emergency Response Unit",
                "SAF_EMR - Emergency Medical Responder",
                "SAF_EMT - Emergency Medical Technician",
                "SAF_AMB - Ambulance Crew",
                "SAF_FA - First Aid Team",
                "VOL_143L - Red Cross 143 Leader",
                "VOL_143V - Red Cross 143 Volunteer"
        };

        JComboBox<String> clsBox = new JComboBox<>(classifications);
        JPanel p = new JPanel(new GridLayout(0, 1, 6, 6));
        p.add(new JLabel("Promote " + name + " to Responder role."));
        p.add(new JLabel("Assign Initial Classification:"));
        p.add(clsBox);

        int res = JOptionPane.showConfirmDialog(this, p, "Promote Account to Responder", JOptionPane.OK_CANCEL_OPTION);
        if (res != JOptionPane.OK_OPTION) return;

        String selected = (String) clsBox.getSelectedItem();
        String code = selected != null ? selected.split(" - ")[0].trim() : "DMS_SAR";
        String division = code.startsWith("SAF_") ? "SAFETY" : (code.startsWith("VOL_") ? "VOLUNTEER" : "DMS");

        state.setText("Promoting account...");
        BackgroundTask.run(() -> {
            ctx.rest.rpc("promote_to_responder", Map.of(
                    "p_profile", UUID.fromString(id),
                    "p_classification_code", code,
                    "p_division_code", division
            ));
            return Boolean.TRUE;
        }, ok -> {
            JOptionPane.showMessageDialog(this, name + " has been promoted to Responder successfully!", "Promotion Completed", JOptionPane.INFORMATION_MESSAGE);
            load();
        }, err -> {
            JOptionPane.showMessageDialog(this, "Promotion failed: " + err.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            load();
        });
    }

    private void addAccount() {
        JTextField name = new JTextField();
        JTextField email = new JTextField();
        JTextField phone = new JTextField();
        JPasswordField password = new JPasswordField();
        JComboBox<String> role = new JComboBox<>(new String[]{"guest", "responder", "admin"});

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Full name:")); form.add(name);
        form.add(new JLabel("Email:")); form.add(email);
        form.add(new JLabel("Phone:")); form.add(phone);
        form.add(new JLabel("Password (min 8 chars):")); form.add(password);
        form.add(new JLabel("Role:")); form.add(role);

        if (JOptionPane.showConfirmDialog(this, form, "Add GERMS Account", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        if (name.getText().isBlank() || email.getText().isBlank() || password.getPassword().length < 8) {
            JOptionPane.showMessageDialog(this, "Name and email are required; password must be at least 8 characters.", "Invalid account", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String selRole = (String) role.getSelectedItem();
        BackgroundTask.run(() -> {
            // Direct signup / creation
            try {
                ctx.rest.invokeFunction("admin-create-user", Map.of(
                        "full_name", name.getText(),
                        "email", email.getText(),
                        "phone", phone.getText(),
                        "password", new String(password.getPassword()),
                        "role", selRole
                ));
            } catch (Exception e) {
                // If edge function not configured, use client auth signup fallback
                ctx.auth.register(email.getText(), new String(password.getPassword()), name.getText(), phone.getText());
            }
            return Boolean.TRUE;
        }, ok -> {
            state.setText("Account created.");
            load();
        }, e -> JOptionPane.showMessageDialog(this, "Account creation failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
    }

    private void editSelected() {
        String id = selectedId();
        if (id == null) return;
        int row = table.convertRowIndexToModel(table.getSelectedRow());
        JTextField name = new JTextField(String.valueOf(model.getValueAt(row, 1)));
        JTextField email = new JTextField(String.valueOf(model.getValueAt(row, 2)));
        JComboBox<String> role = new JComboBox<>(new String[]{"guest", "responder", "admin"});
        role.setSelectedItem(String.valueOf(model.getValueAt(row, 3)).toLowerCase());
        JCheckBox active = new JCheckBox("Active Account", "Active".equalsIgnoreCase(String.valueOf(model.getValueAt(row, 5))));

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Full name")); form.add(name);
        form.add(new JLabel("Email")); form.add(email);
        form.add(new JLabel("Role")); form.add(role);
        form.add(new JLabel("Status")); form.add(active);

        if (JOptionPane.showConfirmDialog(this, form, "Update Account", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;

        BackgroundTask.run(() -> {
            ctx.rest.rpc("update_profile_admin", Map.of(
                    "p_profile", UUID.fromString(id),
                    "p_full_name", name.getText(),
                    "p_email", email.getText(),
                    "p_phone", "",
                    "p_role", role.getSelectedItem(),
                    "p_active", active.isSelected()));
            return Boolean.TRUE;
        }, ok -> {
            state.setText("Account updated.");
            load();
        }, e -> JOptionPane.showMessageDialog(this, e.getMessage(), "Update failed", JOptionPane.ERROR_MESSAGE));
    }

    private void setAvailability() {
        String id = selectedId();
        if (id == null) return;
        String[] statuses = {"available", "standby", "unavailable", "off_duty"};
        String selected = (String) JOptionPane.showInputDialog(this, "Responder readiness status:",
                "Set Availability", JOptionPane.PLAIN_MESSAGE, null, statuses, statuses[0]);
        if (selected == null) return;

        BackgroundTask.run(() -> {
            ctx.rest.rpc("admin_set_responder_availability", Map.of(
                    "p_profile", UUID.fromString(id),
                    "p_status", selected,
                    "p_note", "Updated by command staff"));
            return Boolean.TRUE;
        }, ok -> {
            state.setText("Responder availability updated.");
            load();
        }, e -> JOptionPane.showMessageDialog(this, e.getMessage(), "Availability update failed", JOptionPane.ERROR_MESSAGE));
    }

    private void requestDeployment() {
        String id = selectedId();
        if (id == null) return;

        BackgroundTask.run(() -> ctx.incidents.visibleTo(false), incidents -> {
            java.util.List<Incident> active = incidents.stream().filter(i -> i.status().isActive()).toList();
            if (active.isEmpty()) {
                JOptionPane.showMessageDialog(this, "There are no active incidents available for deployment.", "No Incidents", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            JComboBox<Incident> picker = new JComboBox<>(active.toArray(Incident[]::new));
            JTextField instructions = new JTextField();
            JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
            form.add(new JLabel("Incident:")); form.add(picker);
            form.add(new JLabel("Instructions:")); form.add(instructions);

            if (JOptionPane.showConfirmDialog(this, form, "Request Responder Deployment",
                    JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;

            Incident incident = (Incident) picker.getSelectedItem();
            if (incident == null) return;

            BackgroundTask.run(() -> {
                ctx.rest.rpc("request_responder_deployment", Map.of(
                        "p_incident", UUID.fromString(incident.id()),
                        "p_responder", UUID.fromString(id),
                        "p_instructions", instructions.getText()));
                return Boolean.TRUE;
            }, ok -> {
                state.setText("Deployment request sent to responder.");
                JOptionPane.showMessageDialog(this, "Deployment request sent to responder successfully.", "Deployment Dispatched", JOptionPane.INFORMATION_MESSAGE);
            }, e -> JOptionPane.showMessageDialog(this, e.getMessage(), "Deployment request failed", JOptionPane.ERROR_MESSAGE));
        }, e -> JOptionPane.showMessageDialog(this, e.getMessage(), "Could not load incidents", JOptionPane.ERROR_MESSAGE));
    }

    private void deactivateSelected() {
        String id = selectedId();
        if (id == null) return;
        if (JOptionPane.showConfirmDialog(this, "Deactivate the selected account?", "Confirm Deactivation", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;

        BackgroundTask.run(() -> {
            ctx.rest.rpc("deactivate_profile_admin", Map.of("p_profile", UUID.fromString(id)));
            return Boolean.TRUE;
        }, ok -> {
            state.setText("Account deactivated.");
            load();
        }, e -> JOptionPane.showMessageDialog(this, e.getMessage(), "Deactivation failed", JOptionPane.ERROR_MESSAGE));
    }
}
