package com.gudo.germs.ui.guest;

import com.gudo.germs.models.Profile;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.services.IncidentService;
import com.gudo.germs.ui.components.LocationPickerDialog;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

/** Public, preliminary incident report. It enters the workflow as an unverified Draft for staff to review. */
public final class GuestReportPanel extends JPanel {
    private final IncidentService service;
    private final JTextField title = new JTextField();
    private final JTextField location = new JTextField();
    private final JComboBox<String> type = new JComboBox<>(
            new String[] {"Flood", "Fire", "Landslide", "Typhoon", "Vehicular accident", "Medical emergency", "Other"});
    private final JComboBox<String> severity = new JComboBox<>(new String[] {"low", "moderate", "high", "critical"});
    private final JLabel selectedLocationText = new JLabel("No map location selected (optional)");
    private LocationPickerDialog.Selection selectedLocation;
    private final JButton submit = new JButton("Submit report");
    private final JLabel message = new JLabel(" ");

    public GuestReportPanel(AppContext ctx, Profile user) {
        this.service = ctx.incidents;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        JLabel head = new JLabel("Report an Incident");
        head.setFont(head.getFont().deriveFont(Font.BOLD, 22f));
        head.setAlignmentX(LEFT_ALIGNMENT);
        JLabel note = new JLabel("<html>Your report is preliminary and unverified. A dispatcher will review it "
                + "before it is published. For life-threatening emergencies, call 911 first.</html>");
        note.setForeground(GermsColors.SECONDARY_TEXT);
        note.setAlignmentX(LEFT_ALIGNMENT);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 10));
        form.setAlignmentX(LEFT_ALIGNMENT);
        form.setMaximumSize(new Dimension(700, 220));
        severity.setSelectedItem("moderate");
        form.add(new JLabel("What happened?")); form.add(title);
        form.add(new JLabel("Type")); form.add(type);
        form.add(new JLabel("Location")); form.add(location);
        form.add(new JLabel("How severe?")); form.add(severity);
        JButton chooseLocation = new JButton("Pin / Search on Map");
        chooseLocation.addActionListener(event -> {
            LocationPickerDialog.Selection picked = LocationPickerDialog.showDialog(
                    SwingUtilities.getWindowAncestor(this), "Select Report Location", selectedLocation);
            if (picked != null) {
                selectedLocation = picked;
                selectedLocationText.setText(String.format(java.util.Locale.ROOT, "%s (%.5f, %.5f)",
                        picked.label(), picked.latitude(), picked.longitude()));
                if (location.getText().isBlank()) location.setText(picked.label());
            }
        });
        JPanel mapLocation = new JPanel(new BorderLayout(8, 0));
        mapLocation.add(chooseLocation, BorderLayout.WEST);
        mapLocation.add(selectedLocationText, BorderLayout.CENTER);
        form.add(new JLabel("Map location")); form.add(mapLocation);

        submit.setBackground(GermsColors.PRIMARY_RED);
        submit.setForeground(java.awt.Color.WHITE);
        submit.setAlignmentX(LEFT_ALIGNMENT);
        submit.addActionListener(e -> send(user));
        message.setAlignmentX(LEFT_ALIGNMENT);

        add(head);
        add(Box.createVerticalStrut(8));
        add(note);
        add(Box.createVerticalStrut(16));
        add(form);
        add(Box.createVerticalStrut(16));
        add(submit);
        add(Box.createVerticalStrut(10));
        add(message);
        add(Box.createVerticalGlue());
    }

    private void send(Profile user) {
        submit.setEnabled(false);
        String t = title.getText(), l = location.getText();
        String ty = (String) type.getSelectedItem(), sv = (String) severity.getSelectedItem();
        Double latitude = selectedLocation == null ? null : selectedLocation.latitude();
        Double longitude = selectedLocation == null ? null : selectedLocation.longitude();
        BackgroundTask.run(() -> service.submitGuestReport(t, ty, l, sv, user.fullName() + " (public report)",
                latitude, longitude), i -> {
            submit.setEnabled(true);
            title.setText("");
            location.setText("");
            selectedLocation = null;
            selectedLocationText.setText("No map location selected (optional)");
            message.setText("Report received. Reference: " + i.reference() + ". Awaiting dispatcher verification.");
            message.setForeground(GermsColors.SUCCESS);
        }, err -> {
            submit.setEnabled(true);
            message.setText(err.getMessage());
            message.setForeground(GermsColors.CRITICAL);
        });
    }
}
