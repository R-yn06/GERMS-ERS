package com.gudo.germs.ui.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Role;
import com.gudo.germs.models.Incident;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Map;
import java.util.UUID;

public final class UserManagementPanel extends JPanel {
    private final AppContext ctx;
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Name", "Email", "Role", "Active", "Availability", "Deployment"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JLabel state = new JLabel(" ");

    public UserManagementPanel(AppContext ctx, boolean respondersOnly) {
        super(new BorderLayout(0, 12));
        this.ctx = ctx;
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        JLabel title = new JLabel(respondersOnly ? "Responder Management" : "User Management");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel description = new JLabel(respondersOnly
                ? "Manage responder accounts, role, activation, and contact details."
                : "Create, update, and deactivate administrator and responder accounts.");
        description.setForeground(GermsColors.SECONDARY_TEXT);
        JPanel copy = new JPanel(new BorderLayout(0, 4));
        copy.setOpaque(false); copy.add(title, BorderLayout.NORTH); copy.add(description, BorderLayout.SOUTH);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false); header.add(copy, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); actions.setOpaque(false);
        JButton add = new JButton("Add account"); add.addActionListener(e -> addAccount());
        JButton edit = new JButton("Edit selected"); edit.addActionListener(e -> editSelected());
        JButton deactivate = new JButton("Deactivate"); deactivate.addActionListener(e -> deactivateSelected());
        JButton availability = new JButton("Set availability"); availability.addActionListener(e -> setAvailability());
        JButton request = new JButton("Request deployment"); request.addActionListener(e -> requestDeployment());
        if (respondersOnly) { actions.add(availability); actions.add(request); }
        JButton refresh = new JButton("Refresh"); refresh.addActionListener(e -> load(respondersOnly));
        actions.add(add); actions.add(edit); actions.add(deactivate); actions.add(refresh); header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        table.removeColumn(table.getColumnModel().getColumn(0)); table.setAutoCreateRowSorter(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(state, BorderLayout.SOUTH);
        load(respondersOnly);
    }

    private void load(boolean respondersOnly) {
        String filter = respondersOnly ? "&role=eq.responder" : "";
        String related = respondersOnly ? ",responder_availability(status),incident_assignments(status,incident_id)" : "";
        BackgroundTask.run(() -> ctx.rest.select("profiles", "select=id,full_name,email,role,is_active" + related + "&order=full_name.asc" + filter),
                this::render, e -> state.setText("Could not load users: " + e.getMessage()));
    }

    private void render(JsonNode rows) {
        model.setRowCount(0);
        for (JsonNode n : rows) {
            JsonNode availability = n.path("responder_availability");
            if (availability.isArray() && !availability.isEmpty()) availability = availability.get(0);
            JsonNode assignments = n.path("incident_assignments");
            boolean deployed = assignments.isArray() && !assignments.isEmpty();
            model.addRow(new Object[]{n.path("id").asText(), n.path("full_name").asText(),
                n.path("email").asText(), n.path("role").asText(), n.path("is_active").asBoolean(),
                availability.path("status").asText("available"), deployed ? "DEPLOYED" : "NOT DEPLOYED"});
        }
        state.setText(model.getRowCount() + " account(s) loaded.");
    }

    private String selectedId() {
        int row = table.getSelectedRow(); if (row < 0) return null;
        return String.valueOf(model.getValueAt(table.convertRowIndexToModel(row), 0));
    }

    private void addAccount() {
        JTextField name = new JTextField(); JTextField email = new JTextField(); JTextField phone = new JTextField();
        JPasswordField password = new JPasswordField(); JComboBox<String> role = new JComboBox<>(new String[]{"responder","admin"});
        JPanel form = form(name, email, phone, password, role);
        if (JOptionPane.showConfirmDialog(this, form, "Add GERMS account", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        if (name.getText().isBlank() || email.getText().isBlank() || password.getPassword().length < 8) {
            JOptionPane.showMessageDialog(this, "Name and email are required; password must be at least 8 characters.", "Invalid account", JOptionPane.ERROR_MESSAGE); return;
        }
        BackgroundTask.run(() -> ctx.rest.invokeFunction("admin-create-user", Map.of("full_name", name.getText(),
                "email", email.getText(), "phone", phone.getText(), "password", new String(password.getPassword()), "role", role.getSelectedItem())),
                ok -> { state.setText("Account created."); load(false); }, e -> JOptionPane.showMessageDialog(this,
                        accountCreationMessage(e), "Account creation failed", JOptionPane.ERROR_MESSAGE));
    }

    private static String accountCreationMessage(Throwable error) {
        String message = error.getMessage() == null ? "Unknown error" : error.getMessage();
        if (message.toLowerCase(java.util.Locale.ROOT).contains("requested function not found")) {
            return "The admin-create-user Edge Function is not deployed to this Supabase project.\n\n"
                    + "From the repository root, run:\n"
                    + "supabase login\n"
                    + "supabase link --project-ref <your-project-ref>\n"
                    + "supabase functions deploy admin-create-user\n\n"
                    + "Then try adding the account again.";
        }
        return message;
    }

    private JPanel form(JTextField name, JTextField email, JTextField phone, JPasswordField password, JComboBox<String> role) {
        JPanel p = new JPanel(new GridLayout(0, 2, 8, 8));
        p.add(new JLabel("Full name")); p.add(name); p.add(new JLabel("Email")); p.add(email);
        p.add(new JLabel("Phone")); p.add(phone); p.add(new JLabel("Temporary password")); p.add(password);
        p.add(new JLabel("Role")); p.add(role); return p;
    }

    private void editSelected() {
        String id = selectedId(); if (id == null) return;
        int row = table.convertRowIndexToModel(table.getSelectedRow());
        JTextField name = new JTextField(String.valueOf(model.getValueAt(row, 1)));
        JTextField email = new JTextField(String.valueOf(model.getValueAt(row, 2)));
        JComboBox<String> role = new JComboBox<>(new String[]{"responder","admin"}); role.setSelectedItem(model.getValueAt(row, 3));
        JCheckBox active = new JCheckBox("Active", Boolean.parseBoolean(String.valueOf(model.getValueAt(row, 4))));
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8)); form.add(new JLabel("Full name")); form.add(name);
        form.add(new JLabel("Email")); form.add(email); form.add(new JLabel("Role")); form.add(role); form.add(new JLabel("Status")); form.add(active);
        if (JOptionPane.showConfirmDialog(this, form, "Update account", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        BackgroundTask.run(() -> ctx.rest.rpc("update_profile_admin", Map.of("p_profile", UUID.fromString(id),
                "p_full_name", name.getText(), "p_email", email.getText(), "p_phone", "", "p_role", role.getSelectedItem(), "p_active", active.isSelected())),
                ok -> { state.setText("Account updated."); load(false); }, e -> JOptionPane.showMessageDialog(this, e.getMessage(), "Update failed", JOptionPane.ERROR_MESSAGE));
    }

    private void setAvailability() {
        String id = selectedId(); if (id == null) return;
        String[] statuses = {"available", "standby", "unavailable", "off_duty"};
        String selected = (String) JOptionPane.showInputDialog(this, "Responder readiness:",
                "Set availability", JOptionPane.PLAIN_MESSAGE, null, statuses, statuses[0]);
        if (selected == null) return;
        BackgroundTask.run(() -> ctx.rest.rpc("admin_set_responder_availability",
                Map.of("p_profile", UUID.fromString(id), "p_status", selected, "p_note", "Updated by command staff")),
                ok -> { state.setText("Responder availability updated."); load(true); },
                e -> JOptionPane.showMessageDialog(this, e.getMessage(), "Availability update failed", JOptionPane.ERROR_MESSAGE));
    }

    private void requestDeployment() {
        String id = selectedId(); if (id == null) return;
        BackgroundTask.run(() -> ctx.incidents.visibleTo(false), incidents -> {
            java.util.List<Incident> active = incidents.stream().filter(i -> i.status().isActive()).toList();
            if (active.isEmpty()) {
                JOptionPane.showMessageDialog(this, "There are no active incidents available for deployment.", "No incidents", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            JComboBox<Incident> picker = new JComboBox<>(active.toArray(Incident[]::new));
            JTextField instructions = new JTextField();
            JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
            form.add(new JLabel("Incident")); form.add(picker);
            form.add(new JLabel("Instructions")); form.add(instructions);
            if (JOptionPane.showConfirmDialog(this, form, "Request responder deployment",
                    JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            Incident incident = (Incident) picker.getSelectedItem();
            BackgroundTask.run(() -> ctx.rest.rpc("request_responder_deployment", Map.of(
                    "p_incident", UUID.fromString(incident.id()), "p_responder", UUID.fromString(id),
                    "p_instructions", instructions.getText())),
                    ok -> state.setText("Deployment request sent to the responder."),
                    e -> JOptionPane.showMessageDialog(this, e.getMessage(), "Deployment request failed", JOptionPane.ERROR_MESSAGE));
        }, e -> JOptionPane.showMessageDialog(this, e.getMessage(), "Could not load incidents", JOptionPane.ERROR_MESSAGE));
    }

    private void deactivateSelected() {
        String id = selectedId(); if (id == null) return;
        if (JOptionPane.showConfirmDialog(this, "Deactivate the selected account?", "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        BackgroundTask.run(() -> ctx.rest.rpc("deactivate_profile_admin", Map.of("p_profile", UUID.fromString(id))),
                ok -> { state.setText("Account deactivated."); load(false); }, e -> JOptionPane.showMessageDialog(this, e.getMessage(), "Deactivation failed", JOptionPane.ERROR_MESSAGE));
    }
}
