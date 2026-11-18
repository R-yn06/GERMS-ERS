package com.gudo.germs.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.models.Role;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

/**
 * Emergency Contact Directory.
 * Accessible to Administrators for management, and to Responders / Public
 * Guests for critical hotlines,
 * hospital facilities, partner NGOs, and local government coordination numbers.
 */
public final class EmergencyContactDirectoryPanel extends JPanel implements com.gudo.germs.ui.RefreshablePanel {
    private final AppContext ctx;
    private final Profile user;
    private final boolean readOnly;

    private final DefaultTableModel model = new DefaultTableModel(
            new String[] { "ID", "Contact Name", "Hotline / Phone", "Organization", "Category", "Availability",
                    "Address / Location", "Notes", "Location Value", "Public" },
            0) {
        /** Reports whether the table cell can be edited. */
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final JTextField searchField = new JTextField(18);
    private final JLabel statusLabel = new JLabel(" ");

    /** Creates an emergency contact directory panel instance. */
    public EmergencyContactDirectoryPanel(AppContext ctx, Profile user, boolean readOnly) {
        super(new BorderLayout(0, 12));
        this.ctx = ctx;
        this.user = user;
        this.readOnly = readOnly;
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        add(buildHeader(), BorderLayout.NORTH);

        table.setRowHeight(25);
        table.removeColumn(table.getColumnModel().getColumn(0)); // Hide ID
        table.removeColumn(table.getColumn("Notes"));
        table.removeColumn(table.getColumn("Location Value"));
        table.removeColumn(table.getColumn("Public"));
        table.setAutoCreateRowSorter(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        loadContacts();
    }

    /** Builds header. */
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 10));
        header.setOpaque(false);

        // Row 1: title + subtitle
        JLabel title = new JLabel("Emergency Contacts Directory");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel desc = new JLabel(
                "Verified emergency hotlines, medical trauma centers, rescue organizations, and partner response agencies.");
        desc.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel titles = new JPanel(new GridLayout(0, 1, 0, 2));
        titles.setOpaque(false);
        titles.add(title);
        titles.add(desc);
        header.add(titles, BorderLayout.NORTH);

        // Row 2: search + action buttons
        searchField.addActionListener(e -> loadContacts());
        // Keep the search box at a fixed size so BoxLayout doesn't stretch it.
        searchField.setMaximumSize(searchField.getPreferredSize());

        JButton filterBtn = new JButton("Filter");
        filterBtn.addActionListener(e -> loadContacts());

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadContacts());

        // BoxLayout keeps everything in one row at its natural size (no wrapping).
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));

        actions.add(new JLabel("Search:"));
        actions.add(Box.createHorizontalStrut(8));
        actions.add(searchField);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(filterBtn);
        actions.add(Box.createHorizontalStrut(8));

        if (!readOnly && user.role() == Role.ADMIN) {
            JButton addBtn = new JButton("+ Add Contact");
            addBtn.setBackground(GermsColors.PRIMARY_RED);
            addBtn.setForeground(Color.WHITE);
            addBtn.addActionListener(e -> showAddContactDialog());

            JButton editBtn = new JButton("Edit Contact");
            editBtn.addActionListener(e -> editSelectedContact());

            JButton deleteBtn = new JButton("Delete Contact");
            deleteBtn.addActionListener(e -> deleteSelectedContact());

            actions.add(addBtn);
            actions.add(Box.createHorizontalStrut(8));
            actions.add(editBtn);
            actions.add(Box.createHorizontalStrut(8));
            actions.add(deleteBtn);
            actions.add(Box.createHorizontalStrut(8));
        }

        actions.add(refresh);
        actions.add(Box.createHorizontalGlue()); // pushes everything to the left

        header.add(actions, BorderLayout.CENTER);
        return header;
    }

    /** Loads contacts. */
    public void loadContacts() {
        statusLabel.setText("Querying emergency contacts...");
        String query = searchField.getText().trim();
        String filter = "";
        if (!query.isEmpty()) {
            filter = "&or=(name.ilike.*" + query + "*,organization.ilike.*" + query + "*,category.ilike.*" + query
                    + "*)";
        }

        String finalFilter = filter;
        BackgroundTask.run(() -> {
            return ctx.rest.select("emergency_contacts", "select=*&order=category.asc,name.asc" + finalFilter);
        }, rows -> {
            model.setRowCount(0);
            if (rows != null && rows.isArray()) {
                for (JsonNode c : rows) {
                    model.addRow(new Object[] {
                            c.path("id").asText(),
                            c.path("name").asText(),
                            c.path("contact_number").asText(),
                            c.path("organization").asText(),
                            c.path("category").asText(),
                            c.path("availability").asText("24/7"),
                            c.path("address").asText(c.path("location").asText("")),
                            c.path("notes").asText(""),
                            c.path("location").asText(""),
                            c.path("is_public").asBoolean(true)
                    });
                }
            }
            statusLabel.setText(model.getRowCount() + " emergency contact(s) available.");
        }, err -> {
            statusLabel.setText("Could not load contacts: " + err.getMessage());
        });
    }

    /** Displays add contact dialog. */
    private void showAddContactDialog() {
        JTextField name = new JTextField();
        JTextField number = new JTextField();
        JTextField org = new JTextField();
        JComboBox<String> category = new JComboBox<>(new String[] {
                "Emergency Hotline", "Hospital / Medical", "Rescue Organization", "LGU / Government", "Partner Agency",
                "Police / Security", "Fire Department"
        });
        JTextField availability = new JTextField("24/7");
        JTextField address = new JTextField();
        JTextField location = new JTextField();
        JTextField notes = new JTextField();
        JCheckBox isPublic = new JCheckBox("Show in public directory", true);

        JPanel p = new JPanel(new GridLayout(0, 2, 8, 8));
        p.add(new JLabel("Contact / Facility Name:"));
        p.add(name);
        p.add(new JLabel("Contact Number:"));
        p.add(number);
        p.add(new JLabel("Organization:"));
        p.add(org);
        p.add(new JLabel("Category:"));
        p.add(category);
        p.add(new JLabel("Operational Availability:"));
        p.add(availability);
        p.add(new JLabel("Address / Location:"));
        p.add(address);
        p.add(new JLabel("Map / Area Location:"));
        p.add(location);
        p.add(new JLabel("Notes:"));
        p.add(notes);
        p.add(new JLabel("Visibility:"));
        p.add(isPublic);

        int choice = JOptionPane.showConfirmDialog(this, p, "Add Emergency Contact", JOptionPane.OK_CANCEL_OPTION);
        if (choice != JOptionPane.OK_OPTION)
            return;

        String contactName = name.getText().trim();
        String contactNum = number.getText().trim();
        if (contactName.isEmpty() || contactNum.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name and contact number are required.", "Validation Error",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        BackgroundTask.run(() -> {
            ctx.rest.insertMinimal("emergency_contacts", Map.of(
                    "name", contactName,
                    "contact_number", contactNum,
                    "organization", org.getText().trim(),
                    "category", category.getSelectedItem(),
                    "availability", availability.getText().trim(),
                    "address", address.getText().trim(),
                    "location", location.getText().trim(),
                    "notes", notes.getText().trim(),
                    "is_public", isPublic.isSelected()));
            return Boolean.TRUE;
        }, ok -> {
            loadContacts();
            JOptionPane.showMessageDialog(this, "Contact added to directory.", "Success",
                    JOptionPane.INFORMATION_MESSAGE);
        }, err -> JOptionPane.showMessageDialog(this, "Failed to add contact: " + err.getMessage(), "Error",
                JOptionPane.ERROR_MESSAGE));
    }

    /** Edits the selected emergency contact. */
    private void editSelectedContact() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a contact first.", "Notice",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int row = table.convertRowIndexToModel(selectedRow);
        String id = String.valueOf(model.getValueAt(row, 0));
        JTextField name = new JTextField(String.valueOf(model.getValueAt(row, 1)));
        JTextField number = new JTextField(String.valueOf(model.getValueAt(row, 2)));
        JTextField org = new JTextField(String.valueOf(model.getValueAt(row, 3)));
        JComboBox<String> category = new JComboBox<>(new String[] {
                "Emergency Hotline", "Hospital / Medical", "Rescue Organization", "LGU / Government", "Partner Agency",
                "Police / Security", "Fire Department"
        });
        String currentCategory = String.valueOf(model.getValueAt(row, 4));
        boolean categoryFound = false;
        for (int i = 0; i < category.getItemCount(); i++) {
            if (category.getItemAt(i).equals(currentCategory)) {
                category.setSelectedIndex(i);
                categoryFound = true;
                break;
            }
        }
        if (!categoryFound) {
            category.addItem(currentCategory);
            category.setSelectedItem(currentCategory);
        }
        JTextField availability = new JTextField(String.valueOf(model.getValueAt(row, 5)));
        JTextField address = new JTextField(String.valueOf(model.getValueAt(row, 6)));
        JTextField notes = new JTextField(String.valueOf(model.getValueAt(row, 7)));
        JTextField location = new JTextField(String.valueOf(model.getValueAt(row, 8)));
        JCheckBox isPublic = new JCheckBox("Show in public directory",
                Boolean.TRUE.equals(model.getValueAt(row, 9)));

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Contact / Facility Name:"));
        form.add(name);
        form.add(new JLabel("Contact Number:"));
        form.add(number);
        form.add(new JLabel("Organization:"));
        form.add(org);
        form.add(new JLabel("Category:"));
        form.add(category);
        form.add(new JLabel("Operational Availability:"));
        form.add(availability);
        form.add(new JLabel("Address:"));
        form.add(address);
        form.add(new JLabel("Map / Area Location:"));
        form.add(location);
        form.add(new JLabel("Notes:"));
        form.add(notes);
        form.add(new JLabel("Visibility:"));
        form.add(isPublic);

        if (JOptionPane.showConfirmDialog(this, form, "Edit Emergency Contact",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }

        String contactName = name.getText().trim();
        String contactNumber = number.getText().trim();
        if (contactName.isEmpty() || contactNumber.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name and contact number are required.", "Validation Error",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        BackgroundTask.run(() -> {
            JsonNode updated = ctx.rest.patch("emergency_contacts", "id=eq." + id, Map.of(
                    "name", contactName,
                    "contact_number", contactNumber,
                    "organization", org.getText().trim(),
                    "category", category.getSelectedItem(),
                    "availability", availability.getText().trim(),
                    "address", address.getText().trim(),
                    "location", location.getText().trim(),
                    "notes", notes.getText().trim(),
                    "is_public", isPublic.isSelected()));
            if (!updated.isArray() || updated.isEmpty()) {
                throw new IllegalStateException("The emergency contact was not updated.");
            }
            return Boolean.TRUE;
        }, ok -> loadContacts(), err -> JOptionPane.showMessageDialog(this,
                "Contact update failed: " + err.getMessage(), "Update failed", JOptionPane.ERROR_MESSAGE));
    }

    /** Deletes the selected emergency contact. */
    private void deleteSelectedContact() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a contact first.", "Notice",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int row = table.convertRowIndexToModel(selectedRow);
        String id = String.valueOf(model.getValueAt(row, 0));
        String name = String.valueOf(model.getValueAt(row, 1));
        if (JOptionPane.showConfirmDialog(this, "Delete emergency contact " + name + "? This cannot be undone.",
                "Delete Emergency Contact", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }

        BackgroundTask.run(() -> {
            JsonNode deleted = ctx.rest.delete("emergency_contacts", "id=eq." + id);
            if (!deleted.isArray() || deleted.isEmpty()) {
                throw new IllegalStateException("The emergency contact was not deleted.");
            }
            return Boolean.TRUE;
        }, ok -> loadContacts(), err -> JOptionPane.showMessageDialog(this,
                "Contact deletion failed: " + err.getMessage(), "Delete failed", JOptionPane.ERROR_MESSAGE));
    }

    /** Refreshes data. */
    @Override
    public void refreshData() {
        loadContacts();
    }
}
