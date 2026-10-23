package com.gudo.germs.ui.components;

import com.gudo.germs.security.Module;
import com.gudo.germs.ui.theme.GermsColors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Font;

/** Honest stand-in for modules scheduled for a later phase. Shows no fake data. */
public final class ModulePlaceholderPanel extends JPanel {
    public ModulePlaceholderPanel(Module module) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));
        JLabel title = new JLabel(module.title());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel desc = new JLabel(module.description());
        JLabel status = new JLabel("Not yet implemented. Scheduled for Phase " + module.phase() + ".");
        status.setForeground(GermsColors.SECONDARY_TEXT);
        add(title);
        add(javax.swing.Box.createVerticalStrut(8));
        add(desc);
        add(javax.swing.Box.createVerticalStrut(16));
        add(status);
    }
}
