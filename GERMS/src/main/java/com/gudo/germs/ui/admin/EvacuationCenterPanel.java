package com.gudo.germs.ui.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.models.Role;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Map;
import java.util.UUID;

/**
 * Evacuation Center & Shelter Management.
 * Supports shelter registration, capacity vs occupancy monitoring,
 * status updates (Open, Full, Temporarily Closed, Closed), and location coordinates.
 */
public final class EvacuationCenterPanel extends JPanel {
    private final AppContext ctx;
    private final Profile user;
    private final boolean readOnly;

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Shelter Name", "Address / Barangay", "Capacity", "Occupancy", "Available Space", "Status", "Coordinates"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JLabel statusLabel = new JLabel(" ");

    public EvacuationCenterPanel(AppContext ctx, Profile user, boolean readOnly) {
        super(new BorderLayout(0, 12));
        this.ctx = ctx;
        this.user = user;
        this.readOnly = readOnly;
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        add(buildHeader(), BorderLayout.NORTH);
        table.setRowHeight(25);
        table.removeColumn(table.getColumnModel().getColumn(0)); // Hide ID
        table.setAutoCreateRowSorter(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        loadCenters();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel(readOnly ? "Evacuation Centers & Shelters" : "Evacuation Center Management");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel desc = new JLabel(readOnly
                ? "Public shelter directory, available capacity, and current operating statuses."
                : "Manage shelter facilities, live occupancy numbers, operating status, and disaster preparedness sites.");
        desc.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel p = new JPanel(new GridLayout(0, 1, 0, 2));
        p.setOpaque(false);
        p.add(title);
        p.add(desc);
        header.add(p, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        if (!readOnly && user.role() == Role.ADMIN) {
            JButton addBtn = new JButton("+ Register Shelter");
            addBtn.setBackground(GermsColors.PRIMARY_RED);
            addBtn.setForeground(Color.WHITE);
            addBtn.addActionListener(e -> showAddCenterDialog());

            JButton updateOccupancy = new JButton("Update Occupancy");
            updateOccupancy.addActionListener(e -> showUpdateOccupancyDialog());

            JButton updateStatus = new JButton("Change Status");
            updateStatus.addActionListener(e -> showChangeStatusDialog());

            actions.add(addBtn);
            actions.add(updateOccupancy);
            actions.add(updateStatus);
        }

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadCenters());
        actions.add(refresh);

        header.add(actions, BorderLayout.EAST);
        return header;
    }

    public void loadCenters() {
        statusLabel.setText("Loading evacuation centers from Supabase...");
        String filter = readOnly ? "&status=neq.closed" : "";
        BackgroundTask.run(() -> {
            return ctx.rest.select("evacuation_centers", "select=*&order=name.asc" + filter);
        }, rows -> {
            model.setRowCount(0);
            int totalCap = 0;
            int totalOcc = 0;
            if (rows != null && rows.isArray()) {
                for (JsonNode c : rows) {
                    int cap = c.path("capacity").asInt();
                    int occ = c.path("occupancy").asInt();
                    int avail = Math.max(0, cap - occ);
                    totalCap += cap;
                    totalOcc += occ;

                    String coords = "\u2014";
                    if (c.hasNonNull("latitude") && c.hasNonNull("longitude")) {
                        coords = String.format(java.util.Locale.ROOT, "%.4f, %.4f", c.path("latitude").asDouble(), c.path("longitude").asDouble());
                    }

                    model.addRow(new Object[]{
                            c.path("id").asText(),
                            c.path("name").asText(),
                            c.path("address").asText(),
                            cap,
                            occ,
                            avail,
                            c.path("status").asText().toUpperCase(),
                            coords
                    });
                }
            }
            statusLabel.setText(String.format("Loaded %d shelter(s). Total Capacity: %d | Total Occupants: %d | Remaining Space: %d",
                    model.getRowCount(), totalCap, totalOcc, Math.max(0, totalCap - totalOcc)));
        }, err -> {
            statusLabel.setText("Failed to load centers: " + err.getMessage());
        });
    }

    private String getSelectedCenterId() {
        int r = table.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Please select an evacuation center first.", "Notice", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return String.valueOf(model.getValueAt(table.convertRowIndexToModel(r), 0));
    }

    private void showAddCenterDialog() {
        JTextField name = new JTextField();
        JTextField address = new JTextField();
        JSpinner capacity = new JSpinner(new SpinnerNumberModel(250, 10, 50000, 10));
        JTextField lat = new JTextField("7.0731");
        JTextField lon = new JTextField("125.6128");
        JComboBox<String> status = new JComboBox<>(new String[]{"open", "full", "closed", "maintenance"});

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Center Name:")); form.add(name);
        form.add(new JLabel("Address / Barangay:")); form.add(address);
        form.add(new JLabel("Total Capacity:")); form.add(capacity);
        form.add(new JLabel("Latitude:")); form.add(lat);
        form.add(new JLabel("Longitude:")); form.add(lon);
        form.add(new JLabel("Initial Status:")); form.add(status);

        int res = JOptionPane.showConfirmDialog(this, form, "Register Evacuation Shelter", JOptionPane.OK_CANCEL_OPTION);
        if (res != JOptionPane.OK_OPTION) return;

        String centerName = name.getText().trim();
        if (centerName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Shelter name is required.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        BackgroundTask.run(() -> {
            ctx.rest.insertMinimal("evacuation_centers", Map.of(
                    "name", centerName,
                    "address", address.getText().trim(),
                    "capacity", capacity.getValue(),
                    "occupancy", 0,
                    "status", status.getSelectedItem(),
                    "latitude", Double.parseDouble(lat.getText().trim()),
                    "longitude", Double.parseDouble(lon.getText().trim()),
                    "created_by", user.id().toString()
            ));
            return Boolean.TRUE;
        }, ok -> {
            loadCenters();
            JOptionPane.showMessageDialog(this, "Evacuation center registered successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
        }, err -> JOptionPane.showMessageDialog(this, "Registration failed: " + err.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
    }

    private void showUpdateOccupancyDialog() {
        String id = getSelectedCenterId();
        if (id == null) return;
        int row = table.convertRowIndexToModel(table.getSelectedRow());
        String name = String.valueOf(model.getValueAt(row, 1));
        int cap = (int) model.getValueAt(row, 3);
        int curOcc = (int) model.getValueAt(row, 4);

        JSpinner occSpinner = new JSpinner(new SpinnerNumberModel(curOcc, 0, cap * 2, 1));
        JPanel p = new JPanel(new GridLayout(0, 2, 8, 8));
        p.add(new JLabel("Shelter:")); p.add(new JLabel(name));
        p.add(new JLabel("Max Capacity:")); p.add(new JLabel(String.valueOf(cap)));
        p.add(new JLabel("Current Occupancy:")); p.add(occSpinner);

        int choice = JOptionPane.showConfirmDialog(this, p, "Update Shelter Occupancy", JOptionPane.OK_CANCEL_OPTION);
        if (choice != JOptionPane.OK_OPTION) return;

        int newOcc = (int) occSpinner.getValue();
        String newStatus = newOcc >= cap ? "full" : "open";

        BackgroundTask.run(() -> {
            ctx.rest.select("evacuation_centers", "id=eq." + id); // check exists
            // Update via Supabase Http or RPC
            // PostgREST patch via SupabaseHttp
            ctx.rest.insertMinimal("evacuation_centers", Map.of(
                    "id", id,
                    "occupancy", newOcc,
                    "status", newStatus
            ));
            return Boolean.TRUE;
        }, ok -> loadCenters(), err -> loadCenters());
    }

    private void showChangeStatusDialog() {
        String id = getSelectedCenterId();
        if (id == null) return;
        String[] options = {"open", "full", "closed", "maintenance"};
        String sel = (String) JOptionPane.showInputDialog(this, "Select operating status:",
                "Update Center Status", JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (sel == null) return;

        BackgroundTask.run(() -> {
            ctx.rest.insertMinimal("evacuation_centers", Map.of("id", id, "status", sel));
            return Boolean.TRUE;
        }, ok -> loadCenters(), err -> loadCenters());
    }
}

