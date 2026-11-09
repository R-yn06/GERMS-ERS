package com.gudo.germs.ui.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Incident;
import com.gudo.germs.models.Profile;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Emergency Resource and Equipment Management.
 * Supports inventory tracking, atomic resource allocation to incidents,
 * condition updates, and release of deployed equipment.
 */
public final class ResourceManagementPanel extends JPanel {
    private final AppContext ctx;
    private final Profile user;

    private final DefaultTableModel resourceModel = new DefaultTableModel(
            new String[]{"ID", "Resource Name", "Category", "Total", "Available", "Condition", "Status", "Location"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable resourceTable = new JTable(resourceModel);

    private final DefaultTableModel allocModel = new DefaultTableModel(
            new String[]{"Allocation ID", "Incident", "Allocated Qty", "Allocated By", "Status", "Notes"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable allocTable = new JTable(allocModel);

    private final JLabel statusLabel = new JLabel(" ");

    public ResourceManagementPanel(AppContext ctx, Profile user) {
        super(new BorderLayout(0, 12));
        this.ctx = ctx;
        this.user = user;
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildSplit(), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        loadResources();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Resource & Equipment Inventory");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel desc = new JLabel("Manage medical equipment, rescue gear, vehicles, supplies, and atomic deployment allocation.");
        desc.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel p = new JPanel(new GridLayout(0, 1, 0, 2));
        p.setOpaque(false);
        p.add(title);
        p.add(desc);
        header.add(p, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        JButton addBtn = new JButton("+ Add Resource");
        addBtn.setBackground(GermsColors.PRIMARY_RED);
        addBtn.setForeground(Color.WHITE);
        addBtn.addActionListener(e -> showAddResourceDialog());

        JButton allocBtn = new JButton("Allocate to Incident");
        allocBtn.addActionListener(e -> allocateSelectedResource());

        JButton releaseBtn = new JButton("Release Allocated");
        releaseBtn.addActionListener(e -> releaseSelectedAllocation());

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> loadResources());

        actions.add(addBtn);
        actions.add(allocBtn);
        actions.add(releaseBtn);
        actions.add(refreshBtn);

        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JComponent buildSplit() {
        resourceTable.setRowHeight(25);
        resourceTable.removeColumn(resourceTable.getColumnModel().getColumn(0)); // Hide ID
        resourceTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadAllocationsForSelected();
            }
        });

        allocTable.setRowHeight(24);
        allocTable.removeColumn(allocTable.getColumnModel().getColumn(0)); // Hide ID

        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        JLabel l1 = new JLabel("Emergency Equipment & Supplies Stock");
        l1.setFont(l1.getFont().deriveFont(Font.BOLD, 14f));
        top.add(l1, BorderLayout.NORTH);
        top.add(new JScrollPane(resourceTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.setOpaque(false);
        JLabel l2 = new JLabel("Incident Allocations & Deployment History");
        l2.setFont(l2.getFont().deriveFont(Font.BOLD, 14f));
        bottom.add(l2, BorderLayout.NORTH);
        bottom.add(new JScrollPane(allocTable), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        split.setResizeWeight(0.6);
        split.setBorder(null);
        return split;
    }

    public void loadResources() {
        statusLabel.setText("Loading resource inventory...");
        BackgroundTask.run(() -> {
            return ctx.rest.select("resources", "select=*&order=category.asc,name.asc");
        }, rows -> {
            resourceModel.setRowCount(0);
            if (rows != null && rows.isArray()) {
                for (JsonNode r : rows) {
                    resourceModel.addRow(new Object[]{
                            r.path("id").asText(),
                            r.path("name").asText(),
                            r.path("category").asText().replace('_', ' ').toUpperCase(),
                            r.path("quantity").asInt(),
                            r.path("available_quantity").asInt(),
                            r.path("condition").asText().toUpperCase(),
                            r.path("status").asText().toUpperCase(),
                            r.path("location").asText("Central Warehouse")
                    });
                }
            }
            statusLabel.setText(resourceModel.getRowCount() + " resource inventory items loaded.");
            if (resourceTable.getRowCount() > 0 && resourceTable.getSelectedRow() < 0) {
                resourceTable.setRowSelectionInterval(0, 0);
            }
        }, err -> {
            statusLabel.setText("Failed to load resources: " + err.getMessage());
        });
    }

    private String getSelectedResourceId() {
        int r = resourceTable.getSelectedRow();
        if (r < 0) return null;
        return String.valueOf(resourceModel.getValueAt(resourceTable.convertRowIndexToModel(r), 0));
    }

    private void loadAllocationsForSelected() {
        String resId = getSelectedResourceId();
        if (resId == null) {
            allocModel.setRowCount(0);
            return;
        }

        BackgroundTask.run(() -> {
            return ctx.rest.select("resource_allocations",
                    "select=id,quantity,status,notes,incidents(incident_no,title),profiles(full_name)&resource_id=eq." + resId + "&order=allocated_at.desc");
        }, rows -> {
            allocModel.setRowCount(0);
            if (rows != null && rows.isArray()) {
                for (JsonNode a : rows) {
                    JsonNode inc = a.path("incidents");
                    String incName = inc.hasNonNull("title") ? (inc.path("incident_no").asText() + " " + inc.path("title").asText()) : "Incident";
                    JsonNode p = a.path("profiles");
                    String by = p.hasNonNull("full_name") ? p.path("full_name").asText() : "Command Staff";

                    allocModel.addRow(new Object[]{
                            a.path("id").asText(),
                            incName,
                            a.path("quantity").asInt(),
                            by,
                            a.path("status").asText().toUpperCase(),
                            a.path("notes").asText("")
                    });
                }
            }
        }, err -> statusLabel.setText("Failed to load allocations: " + err.getMessage()));
    }

    private void showAddResourceDialog() {
        JTextField name = new JTextField();
        JComboBox<String> category = new JComboBox<>(new String[]{
                "first_aid", "medical_equipment", "ambulance", "rescue_equipment",
                "emergency_supplies", "communication", "evacuation", "generator", "vehicle", "other"
        });
        JSpinner qty = new JSpinner(new SpinnerNumberModel(5, 1, 10000, 1));
        JComboBox<String> condition = new JComboBox<>(new String[]{"new", "good", "fair", "needs_maintenance"});
        JTextField loc = new JTextField("PRC Davao Logistics Hub");

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Equipment Name:")); form.add(name);
        form.add(new JLabel("Category:")); form.add(category);
        form.add(new JLabel("Initial Quantity:")); form.add(qty);
        form.add(new JLabel("Condition:")); form.add(condition);
        form.add(new JLabel("Storage Location:")); form.add(loc);

        int choice = JOptionPane.showConfirmDialog(this, form, "Add New Emergency Resource", JOptionPane.OK_CANCEL_OPTION);
        if (choice != JOptionPane.OK_OPTION) return;

        String resName = name.getText().trim();
        if (resName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name is required.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int quantity = (int) qty.getValue();

        BackgroundTask.run(() -> {
            ctx.rest.insertMinimal("resources", Map.of(
                    "name", resName,
                    "category", category.getSelectedItem(),
                    "quantity", quantity,
                    "available_quantity", quantity,
                    "condition", condition.getSelectedItem(),
                    "location", loc.getText().trim(),
                    "status", "available"
            ));
            return Boolean.TRUE;
        }, ok -> {
            loadResources();
            JOptionPane.showMessageDialog(this, "Resource added successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
        }, err -> JOptionPane.showMessageDialog(this, "Failed to add resource: " + err.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
    }

    private void allocateSelectedResource() {
        String resId = getSelectedResourceId();
        if (resId == null) {
            JOptionPane.showMessageDialog(this, "Please select a resource first.", "Notice", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int r = resourceTable.convertRowIndexToModel(resourceTable.getSelectedRow());
        String name = String.valueOf(resourceModel.getValueAt(r, 1));
        int avail = Integer.parseInt(String.valueOf(resourceModel.getValueAt(r, 4)));

        if (avail <= 0) {
            JOptionPane.showMessageDialog(this, "No inventory available for " + name + " (0 available).", "Out of Stock", JOptionPane.WARNING_MESSAGE);
            return;
        }

        BackgroundTask.run(() -> ctx.incidents.visibleTo(false), incidents -> {
            List<Incident> active = incidents.stream().filter(i -> i.status().isActive()).toList();
            if (active.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No active incidents currently available for allocation.", "Notice", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            JComboBox<Incident> incBox = new JComboBox<>(active.toArray(Incident[]::new));
            JSpinner qtySpinner = new JSpinner(new SpinnerNumberModel(1, 1, avail, 1));
            JTextField notes = new JTextField();

            JPanel p = new JPanel(new GridLayout(0, 2, 8, 8));
            p.add(new JLabel("Resource:")); p.add(new JLabel(name + " (" + avail + " available)"));
            p.add(new JLabel("Destination Incident:")); p.add(incBox);
            p.add(new JLabel("Allocate Quantity:")); p.add(qtySpinner);
            p.add(new JLabel("Dispatch Notes:")); p.add(notes);

            int choice = JOptionPane.showConfirmDialog(this, p, "Allocate Resource to Incident", JOptionPane.OK_CANCEL_OPTION);
            if (choice != JOptionPane.OK_OPTION) return;

            Incident inc = (Incident) incBox.getSelectedItem();
            int allocQty = (int) qtySpinner.getValue();

            BackgroundTask.run(() -> {
                ctx.rest.rpc("allocate_resource", Map.of(
                        "p_incident", UUID.fromString(inc.id()),
                        "p_resource", UUID.fromString(resId),
                        "p_quantity", allocQty,
                        "p_notes", notes.getText().trim()
                ));
                return Boolean.TRUE;
            }, ok -> {
                loadResources();
                JOptionPane.showMessageDialog(this, allocQty + "x " + name + " allocated to " + inc.title() + "!", "Allocation Successful", JOptionPane.INFORMATION_MESSAGE);
            }, err -> JOptionPane.showMessageDialog(this, "Allocation failed: " + err.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
        }, err -> JOptionPane.showMessageDialog(this, "Failed to load incidents: " + err.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
    }

    private void releaseSelectedAllocation() {
        int r = allocTable.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Select an allocated record from the bottom table to release.", "Notice", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = allocTable.convertRowIndexToModel(r);
        String allocId = String.valueOf(allocModel.getValueAt(modelRow, 0));
        String status = String.valueOf(allocModel.getValueAt(modelRow, 4));

        if (!"ALLOCATED".equalsIgnoreCase(status)) {
            JOptionPane.showMessageDialog(this, "This allocation is already " + status + ".", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int choice = JOptionPane.showOptionDialog(this,
                "Release resource back to available inventory or mark as consumed?",
                "Release Resource",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new String[]{"Return to Inventory", "Mark Consumed", "Cancel"},
                "Return to Inventory");

        if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) return;
        boolean consumed = (choice == 1);

        BackgroundTask.run(() -> {
            ctx.rest.rpc("release_resource", Map.of(
                    "p_allocation", UUID.fromString(allocId),
                    "p_consumed", consumed
            ));
            return Boolean.TRUE;
        }, ok -> {
            loadResources();
            JOptionPane.showMessageDialog(this, "Resource status updated successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
        }, err -> JOptionPane.showMessageDialog(this, "Release failed: " + err.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
    }
}

