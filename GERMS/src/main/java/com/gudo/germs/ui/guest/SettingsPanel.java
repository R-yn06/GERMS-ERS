package com.gudo.germs.ui.guest;

import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Profile;
import com.gudo.germs.models.Role;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.ui.theme.ThemeManager;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import java.awt.Font;

/** Settings available to every role, including responder deployment-alert preferences. */
public final class SettingsPanel extends JPanel {
    public SettingsPanel(AppContext ctx, Profile user) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        JLabel title = new JLabel("Settings");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setAlignmentX(LEFT_ALIGNMENT);
        add(title);
        add(Box.createVerticalStrut(16));
        JButton theme = new JButton("Switch between light and dark mode");
        theme.setAlignmentX(LEFT_ALIGNMENT);
        theme.addActionListener(e -> ThemeManager.toggle());
        add(theme);
        add(Box.createVerticalStrut(16));
        if (user.role() == Role.RESPONDER) {
            JCheckBox deploymentAlerts = new JCheckBox("Receive deployment request alerts and sounds", true);
            deploymentAlerts.setAlignmentX(LEFT_ALIGNMENT);
            add(deploymentAlerts);
            add(Box.createVerticalStrut(8));
            JLabel alertHelp = new JLabel("This setting affects deployment requests. Other urgent system alerts still sound.");
            alertHelp.setForeground(GermsColors.SECONDARY_TEXT);
            alertHelp.setAlignmentX(LEFT_ALIGNMENT);
            add(alertHelp);
            BackgroundTask.run(() -> ctx.rest.select("responder_notification_preferences",
                    "select=deployment_notifications_enabled&profile_id=eq." + user.id()), rows -> {
                if (rows != null && rows.isArray() && !rows.isEmpty()) {
                    deploymentAlerts.setSelected(rows.get(0).path("deployment_notifications_enabled").asBoolean(true));
                }
            }, ignored -> { /* The safe default is enabled. */ });
            deploymentAlerts.addActionListener(e -> BackgroundTask.run(() -> {
                ctx.rest.rpc("set_deployment_notification_preference",
                        java.util.Map.of("p_enabled", deploymentAlerts.isSelected()));
                return Boolean.TRUE;
            }, ignored -> { }, error -> {
                deploymentAlerts.setSelected(!deploymentAlerts.isSelected());
                JOptionPane.showMessageDialog(this, "Could not save alert preference: " + error.getMessage(),
                        "Settings", JOptionPane.ERROR_MESSAGE);
            }));
        }
        add(Box.createVerticalStrut(16));
        JLabel who = new JLabel("Signed in as: " + user.fullName() + " (" + user.role().label() + ")");
        who.setForeground(GermsColors.SECONDARY_TEXT);
        who.setAlignmentX(LEFT_ALIGNMENT);
        add(who);
    }
}
