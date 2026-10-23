package com.gudo.germs.ui.guest;

import com.gudo.germs.models.Profile;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.ui.theme.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Font;

/** Settings available to every role (theme now; alert sound and volume in Phase 2). */
public final class SettingsPanel extends JPanel {
    public SettingsPanel(Profile user) {
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
        JLabel who = new JLabel("Signed in as: " + user.fullName() + " (" + user.role().label() + ")");
        who.setForeground(GermsColors.SECONDARY_TEXT);
        who.setAlignmentX(LEFT_ALIGNMENT);
        add(who);
    }
}
