package com.gudo.germs.ui.admin;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.models.Role;
import com.gudo.germs.ui.components.LocationPickerDialog;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

/**
 * Evacuation Center & Shelter Management.
 * Supports shelter registration, capacity vs occupancy monitoring,
 * status updates (Open, Full, Temporarily Closed, Closed), and location
 * coordinates.
 */
public final class EvacuationCenterPanel extends JPanel implements com.gudo.germs.ui.RefreshablePanel {
    private final AppContext ctx;
    private final Profile user;
    private final boolean readOnly;

    private final DefaultTableModel model = new DefaultTableModel(
            new String[] { "ID", "Shelter Name", "Address / Barangay", "Capacity", "Occupancy", "Available Space",
                    "Status", "Coordinates", "Latitude", "Longitude", "Notes" },
            0) {
        /** Reports whether the table cell can be edited. */
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final JLabel statusLabel = new JLabel(" ");

    /** Creates an evacuation center panel instance. */
    public EvacuationCenterPanel(AppContext ctx, Profile user, boolean readOnly) {
        super(new BorderLayout(0, 12));
        this.ctx = ctx;
        this.user = user;
        this.readOnly = readOnly;
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        add(buildHeader(), BorderLayout.NORTH);
        table.setRowHeight(25);
        table.removeColumn(table.getColumnModel().getColumn(0)); // Hide ID
        table.removeColumn(table.getColumn("Latitude"));
        table.removeColumn(table.getColumn("Longitude"));
        table.removeColumn(table.getColumn("Notes"));
        table.setAutoCreateRowSorter(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        loadCenters();
    }

    /** Builds header. */
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 10));
        header.setOpaque(false);

        // Row 1: title + subtitle
        JLabel title = new JLabel(readOnly ? "Evacuation Centers & Shelters" : "Evacuation Center Management");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel desc = new JLabel(readOnly
                ? "Public shelter directory, available capacity, and current operating statuses."
                : "Manage shelter facilities, live occupancy numbers, operating status, and disaster preparedness sites.");
        desc.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel titles = new JPanel(new GridLayout(0, 1, 0, 2));
        titles.setOpaque(false);
        titles.add(title);
        titles.add(desc);
        header.add(titles, BorderLayout.NORTH);

        // Row 2: action buttons
        // BoxLayout keeps everything in one row at its natural size (no wrapping).
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));

        if (!readOnly && user.role() == Role.ADMIN) {
            JButton addBtn = new JButton("+ Register Shelter");
            addBtn.setBackground(GermsColors.PRIMARY_RED);
            addBtn.setForeground(Color.WHITE);
            addBtn.addActionListener(e -> showAddCenterDialog());

            JButton updateOccupancy = new JButton("Update Occupancy");
            updateOccupancy.addActionListener(e -> showUpdateOccupancyDialog());

            JButton updateStatus = new JButton("Change Status");
            updateStatus.addActionListener(e -> showChangeStatusDialog());

            JButton edit = new JButton("Edit Center");
            edit.addActionListener(e -> showEditCenterDialog());

            JButton delete = new JButton("Delete Center");
            delete.addActionListener(e -> deleteSelectedCenter());

            actions.add(addBtn);
            actions.add(Box.createHorizontalStrut(8));
            actions.add(edit);
            actions.add(Box.createHorizontalStrut(8));
            actions.add(delete);
            actions.add(Box.createHorizontalStrut(8));
            actions.add(updateOccupancy);
            actions.add(Box.createHorizontalStrut(8));
            actions.add(updateStatus);
            actions.add(Box.createHorizontalStrut(8));
        }

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadCenters());
        actions.add(refresh);
        actions.add(Box.createHorizontalGlue()); // pushes buttons to the left

        header.add(actions, BorderLayout.CENTER);
        return header;
    }

    /** Loads centers. */
    public void loadCenters() {
        statusLabel.setText("Loading evacuation centers from Supabase...");
        String filter = readOnly ? "&status=neq.closed" : "";
        String columns = readOnly ? "id,name,address,capacity,occupancy,status,latitude,longitude" : "*";
        BackgroundTask.run(() -> {
            return ctx.rest.select("evacuation_centers", "select=" + columns + "&order=name.asc" + filter);
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
                        coords = String.format(java.util.Locale.ROOT, "%.4f, %.4f", c.path("latitude").asDouble(),
                                c.path("longitude").asDouble());
                    }

                    model.addRow(new Object[] {
                            c.path("id").asText(),
                            c.path("name").asText(),
                            c.path("address").asText(),
                            cap,
                            occ,
                            avail,
                            c.path("status").asText().toUpperCase(),
                            coords,
                            c.path("latitude").isNumber() ? c.path("latitude").asDouble() : null,
                            c.path("longitude").isNumber() ? c.path("longitude").asDouble() : null,
                            c.path("notes").asText("")
                    });
                }
            }
            statusLabel.setText(String.format(
                    "Loaded %d shelter(s). Total Capacity: %d | Total Occupants: %d | Remaining Space: %d",
                    model.getRowCount(), totalCap, totalOcc, Math.max(0, totalCap - totalOcc)));
        }, err -> {
            statusLabel.setText("Failed to load centers: " + err.getMessage());
        });
    }

    /** Returns selected center id. */
    private String getSelectedCenterId() {
        int r = table.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Please select an evacuation center first.", "Notice",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return String.valueOf(model.getValueAt(table.convertRowIndexToModel(r), 0));
    }

    /** Displays add center dialog. */
    private void showAddCenterDialog() {
        JTextField name = new JTextField();
        JTextField address = new JTextField();
        JSpinner capacity = new JSpinner(new SpinnerNumberModel(250, 10, 50000, 10));
        LocationPickerDialog.Selection[] selectedLocation = new LocationPickerDialog.Selection[1];
        JLabel selectedLocationText = new JLabel("No map location selected");
        JButton chooseLocation = new JButton("Pin / Search on Map");
        chooseLocation.addActionListener(event -> {
            LocationPickerDialog.Selection picked = LocationPickerDialog.showDialog(
                    SwingUtilities.getWindowAncestor(this), "Select Evacuation Center Location", selectedLocation[0]);
            if (picked != null) {
                selectedLocation[0] = picked;
                selectedLocationText.setText(String.format(java.util.Locale.ROOT, "%s (%.5f, %.5f)",
                        picked.label(), picked.latitude(), picked.longitude()));
                if (address.getText().isBlank())
                    address.setText(picked.label());
            }
        });
        JComboBox<String> status = new JComboBox<>(new String[] { "open", "full", "closed", "maintenance" });

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Center Name:"));
        form.add(name);
        form.add(new JLabel("Address / Barangay:"));
        form.add(address);
        form.add(new JLabel("Total Capacity:"));
        form.add(capacity);
        form.add(new JLabel("Map Location:"));
        JPanel locationPicker = new JPanel(new BorderLayout(8, 0));
        locationPicker.add(chooseLocation, BorderLayout.WEST);
        locationPicker.add(selectedLocationText, BorderLayout.CENTER);
        form.add(locationPicker);
        form.add(new JLabel("Initial Status:"));
        form.add(status);

        int res = JOptionPane.showConfirmDialog(this, form, "Register Evacuation Shelter",
                JOptionPane.OK_CANCEL_OPTION);
        if (res != JOptionPane.OK_OPTION)
            return;

        String centerName = name.getText().trim();
        if (centerName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Shelter name is required.", "Validation Error",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (selectedLocation[0] == null) {
            JOptionPane.showMessageDialog(this, "Select the evacuation center location on the map.",
                    "Location Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        LocationPickerDialog.Selection location = selectedLocation[0];

        BackgroundTask.run(() -> {
            ctx.rest.insertMinimal("evacuation_centers", Map.of(
                    "name", centerName,
                    "address", address.getText().trim(),
                    "capacity", capacity.getValue(),
                    "occupancy", 0,
                    "status", status.getSelectedItem(),
                    "latitude", location.latitude(),
                    "longitude", location.longitude(),
                    "created_by", user.id().toString()));
            return Boolean.TRUE;
        }, ok -> {
            loadCenters();
            JOptionPane.showMessageDialog(this, "Evacuation center registered successfully.", "Success",
                    JOptionPane.INFORMATION_MESSAGE);
        }, err -> JOptionPane.showMessageDialog(this, "Registration failed: " + err.getMessage(), "Error",
                JOptionPane.ERROR_MESSAGE));
    }

    /** Edits the selected evacuation center. */
    private void showEditCenterDialog() {
        String id = getSelectedCenterId();
        if (id == null) {
            return;
        }

        int row = table.convertRowIndexToModel(table.getSelectedRow());
        String currentName = String.valueOf(model.getValueAt(row, 1));
        String currentAddress = String.valueOf(model.getValueAt(row, 2));
        int currentCapacity = ((Number) model.getValueAt(row, 3)).intValue();
        int currentOccupancy = ((Number) model.getValueAt(row, 4)).intValue();
        String currentStatus = String.valueOf(model.getValueAt(row, 6)).toLowerCase();
        Object latitudeValue = model.getValueAt(row, 8);
        Object longitudeValue = model.getValueAt(row, 9);
        Double latitude = latitudeValue instanceof Number value ? value.doubleValue() : null;
        Double longitude = longitudeValue instanceof Number value ? value.doubleValue() : null;
        String currentNotes = String.valueOf(model.getValueAt(row, 10));

        JTextField name = new JTextField(currentName);
        JTextField address = new JTextField(currentAddress);
        JSpinner capacity = new JSpinner(new SpinnerNumberModel(
                currentCapacity, currentOccupancy, Math.max(50000, currentCapacity), 1));
        JComboBox<String> status = new JComboBox<>(new String[] { "open", "full", "closed", "maintenance" });
        status.setSelectedItem(currentStatus);
        JTextField notes = new JTextField(currentNotes);
        LocationPickerDialog.Selection initialLocation = latitude != null && longitude != null
                ? new LocationPickerDialog.Selection(latitude, longitude, currentAddress, "existing", null)
                : null;
        LocationPickerDialog.Selection[] selectedLocation = new LocationPickerDialog.Selection[] { initialLocation };
        JLabel selectedLocationText = new JLabel(latitude == null || longitude == null
                ? "No map location selected"
                : String.format(java.util.Locale.ROOT, "%.5f, %.5f", latitude, longitude));
        JButton chooseLocation = new JButton("Pin / Search on Map");
        chooseLocation.addActionListener(event -> {
            LocationPickerDialog.Selection picked = LocationPickerDialog.showDialog(
                    SwingUtilities.getWindowAncestor(this), "Select Evacuation Center Location", selectedLocation[0]);
            if (picked != null) {
                selectedLocation[0] = picked;
                selectedLocationText.setText(String.format(java.util.Locale.ROOT, "%s (%.5f, %.5f)",
                        picked.label(), picked.latitude(), picked.longitude()));
                if (address.getText().isBlank()) {
                    address.setText(picked.label());
                }
            }
        });

        JPanel locationPicker = new JPanel(new BorderLayout(8, 0));
        locationPicker.add(chooseLocation, BorderLayout.WEST);
        locationPicker.add(selectedLocationText, BorderLayout.CENTER);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Center Name:"));
        form.add(name);
        form.add(new JLabel("Address / Barangay:"));
        form.add(address);
        form.add(new JLabel("Total Capacity:"));
        form.add(capacity);
        form.add(new JLabel("Map Location:"));
        form.add(locationPicker);
        form.add(new JLabel("Status:"));
        form.add(status);
        form.add(new JLabel("Notes:"));
        form.add(notes);

        if (JOptionPane.showConfirmDialog(this, form, "Edit Evacuation Center",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }

        String centerName = name.getText().trim();
        String centerAddress = address.getText().trim();
        if (centerName.isEmpty() || centerAddress.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Center name and address are required.", "Validation Error",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("name", centerName);
        updates.put("address", centerAddress);
        updates.put("capacity", capacity.getValue());
        updates.put("status", status.getSelectedItem());
        updates.put("notes", notes.getText().trim());
        if (selectedLocation[0] != null) {
            updates.put("latitude", selectedLocation[0].latitude());
            updates.put("longitude", selectedLocation[0].longitude());
        }

        BackgroundTask.run(() -> {
            JsonNode updated = ctx.rest.patch("evacuation_centers", "id=eq." + id, updates);
            if (!updated.isArray() || updated.isEmpty()) {
                throw new IllegalStateException("The evacuation center was not updated.");
            }
            return Boolean.TRUE;
        }, ok -> loadCenters(), err -> JOptionPane.showMessageDialog(this,
                "Center update failed: " + err.getMessage(), "Update failed", JOptionPane.ERROR_MESSAGE));
    }

    /** Deletes the selected evacuation center. */
    private void deleteSelectedCenter() {
        String id = getSelectedCenterId();
        if (id == null) {
            return;
        }

        int row = table.convertRowIndexToModel(table.getSelectedRow());
        String name = String.valueOf(model.getValueAt(row, 1));
        if (JOptionPane.showConfirmDialog(this, "Delete evacuation center " + name + "? This cannot be undone.",
                "Delete Evacuation Center", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }

        BackgroundTask.run(() -> {
            JsonNode deleted = ctx.rest.delete("evacuation_centers", "id=eq." + id);
            if (!deleted.isArray() || deleted.isEmpty()) {
                throw new IllegalStateException("The evacuation center was not deleted.");
            }
            return Boolean.TRUE;
        }, ok -> loadCenters(), err -> JOptionPane.showMessageDialog(this,
                "Center deletion failed: " + err.getMessage(), "Delete failed", JOptionPane.ERROR_MESSAGE));
    }

    /** Displays update occupancy dialog. */
    private void showUpdateOccupancyDialog() {
        String id = getSelectedCenterId();
        if (id == null)
            return;
        int row = table.convertRowIndexToModel(table.getSelectedRow());
        String name = String.valueOf(model.getValueAt(row, 1));
        int cap = (int) model.getValueAt(row, 3);
        int curOcc = (int) model.getValueAt(row, 4);

        JSpinner occSpinner = new JSpinner(new SpinnerNumberModel(curOcc, 0, cap * 2, 1));
        JPanel p = new JPanel(new GridLayout(0, 2, 8, 8));
        p.add(new JLabel("Shelter:"));
        p.add(new JLabel(name));
        p.add(new JLabel("Max Capacity:"));
        p.add(new JLabel(String.valueOf(cap)));
        p.add(new JLabel("Current Occupancy:"));
        p.add(occSpinner);

        int choice = JOptionPane.showConfirmDialog(this, p, "Update Shelter Occupancy", JOptionPane.OK_CANCEL_OPTION);
        if (choice != JOptionPane.OK_OPTION)
            return;

        int newOcc = (int) occSpinner.getValue();
        String newStatus = newOcc >= cap ? "full" : "open";

        BackgroundTask.run(() -> {
            JsonNode updated = ctx.rest.patch("evacuation_centers", "id=eq." + id,
                    Map.of("occupancy", newOcc, "status", newStatus));
            if (!updated.isArray() || updated.isEmpty()) {
                throw new IllegalStateException("The evacuation center was not updated.");
            }
            return Boolean.TRUE;
        }, ok -> loadCenters(), err -> JOptionPane.showMessageDialog(this,
                "Occupancy update failed: " + err.getMessage(), "Update failed", JOptionPane.ERROR_MESSAGE));
    }

    /** Displays change status dialog. */
    private void showChangeStatusDialog() {
        String id = getSelectedCenterId();
        if (id == null)
            return;
        String[] options = { "open", "full", "closed", "maintenance" };
        String sel = (String) JOptionPane.showInputDialog(this, "Select operating status:",
                "Update Center Status", JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (sel == null)
            return;

        BackgroundTask.run(() -> {
            JsonNode updated = ctx.rest.patch("evacuation_centers", "id=eq." + id, Map.of("status", sel));
            if (!updated.isArray() || updated.isEmpty()) {
                throw new IllegalStateException("The evacuation center was not updated.");
            }
            return Boolean.TRUE;
        }, ok -> loadCenters(), err -> JOptionPane.showMessageDialog(this,
                "Status update failed: " + err.getMessage(), "Update failed", JOptionPane.ERROR_MESSAGE));
    }

    /** Refreshes data. */
    @Override
    public void refreshData() {
        loadCenters();
    }
}
