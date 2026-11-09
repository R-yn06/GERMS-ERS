package com.gudo.germs.ui.components;

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

/**
 * Emergency Contact Directory.
 * Accessible to Administrators for management, and to Responders / Public Guests for critical hotlines,
 * hospital facilities, partner NGOs, and local government coordination numbers.
 */
public final class EmergencyContactDirectoryPanel extends JPanel {
    private final AppContext ctx;
    private final Profile user;
    private final boolean readOnly;

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Contact Name", "Hotline / Phone", "Organization", "Category", "Availability", "Address / Location"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JTextField searchField = new JTextField(18);
    private final JLabel statusLabel = new JLabel(" ");

    public EmergencyContactDirectoryPanel(AppContext ctx, Profile user, boolean readOnly) {
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

        loadContacts();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Emergency Contacts Directory");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel desc = new JLabel("Verified emergency hotlines, medical trauma centers, rescue organizations, and partner response agencies.");
        desc.setForeground(GermsColors.SECONDARY_TEXT);

        JPanel p = new JPanel(new GridLayout(0, 1, 0, 2));
        p.setOpaque(false);
        p.add(title);
        p.add(desc);
        header.add(p, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        actions.add(new JLabel("Search:"));
        searchField.addActionListener(e -> loadContacts());
        actions.add(searchField);

        JButton filterBtn = new JButton("Filter");
        filterBtn.addActionListener(e -> loadContacts());
        actions.add(filterBtn);

        if (!readOnly && user.role() == Role.ADMIN) {
            JButton addBtn = new JButton("+ Add Contact");
            addBtn.setBackground(GermsColors.PRIMARY_RED);
            addBtn.setForeground(Color.WHITE);
            addBtn.addActionListener(e -> showAddContactDialog());
            actions.add(addBtn);
        }

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadContacts());
        actions.add(refresh);

        header.add(actions, BorderLayout.EAST);
        return header;
    }

    public void loadContacts() {
        statusLabel.setText("Querying emergency contacts...");
        String query = searchField.getText().trim();
        String filter = "";
        if (!query.isEmpty()) {
            filter = "&or=(name.ilike.*" + query + "*,organization.ilike.*" + query + "*,category.ilike.*" + query + "*)";
        }

        String finalFilter = filter;
        BackgroundTask.run(() -> {
            return ctx.rest.select("emergency_contacts", "select=*&order=category.asc,name.asc" + finalFilter);
        }, rows -> {
            model.setRowCount(0);
            if (rows != null && rows.isArray()) {
                for (JsonNode c : rows) {
                    model.addRow(new Object[]{
                            c.path("id").asText(),
                            c.path("name").asText(),
                            c.path("contact_number").asText(),
                            c.path("organization").asText(),
                            c.path("category").asText(),
                            c.path("availability").asText("24/7"),
                            c.path("address").asText(c.path("location").asText(""))
                    });
                }
            }
            statusLabel.setText(model.getRowCount() + " emergency contact(s) available.");
        }, err -> {
            statusLabel.setText("Could not load contacts: " + err.getMessage());
        });
    }

    private void showAddContactDialog() {
        JTextField name = new JTextField();
        JTextField number = new JTextField();
        JTextField org = new JTextField();
        JComboBox<String> category = new JComboBox<>(new String[]{
                "Emergency Hotline", "Hospital / Medical", "Rescue Organization", "LGU / Government", "Partner Agency", "Police / Security", "Fire Department"
        });
        JTextField availability = new JTextField("24/7");
        JTextField address = new JTextField();

        JPanel p = new JPanel(new GridLayout(0, 2, 8, 8));
        p.add(new JLabel("Contact / Facility Name:")); p.add(name);
        p.add(new JLabel("Contact Number:")); p.add(number);
        p.add(new JLabel("Organization:")); p.add(org);
        p.add(new JLabel("Category:")); p.add(category);
        p.add(new JLabel("Operational Availability:")); p.add(availability);
        p.add(new JLabel("Address / Location:")); p.add(address);

        int choice = JOptionPane.showConfirmDialog(this, p, "Add Emergency Contact", JOptionPane.OK_CANCEL_OPTION);
        if (choice != JOptionPane.OK_OPTION) return;

        String contactName = name.getText().trim();
        String contactNum = number.getText().trim();
        if (contactName.isEmpty() || contactNum.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name and contact number are required.", "Validation Error", JOptionPane.WARNING_MESSAGE);
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
                    "is_public", true
            ));
            return Boolean.TRUE;
        }, ok -> {
            loadContacts();
            JOptionPane.showMessageDialog(this, "Contact added to directory.", "Success", JOptionPane.INFORMATION_MESSAGE);
        }, err -> JOptionPane.showMessageDialog(this, "Failed to add contact: " + err.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
    }
}

